package com.airedgesoft.lib.restful.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;

/**
 * 聚合存库入口（持久化侧）：<b>版本基线由框架取给业务，仓储只管存库</b>。
 *
 * <p>
 * <b>为什么需要这一层</b>：Spring Data JDBC 依据实体的版本属性判定 INSERT / UPDATE —— 版本必须在 {@code save} 调用<b>之前</b>就在实体里，
 * 实体回调（{@code BeforeConvert}）发生在判定之后，来不及补版本。因此「谁把版本放进实体」必须由框架在存库入口处完成：框架从 {@link AggregateSnapshots}
 * 取本事务内的加载基线并注入实体，业务无需知道版本从哪来。
 *
 * <p>
 * <b>两种形态</b>：
 * <ul>
 * <li>{@link #save(Object)}：实体版本可写（可变 PO）→ 框架直接播种，业务零版本感知；</li>
 * <li>{@link #save(Object, Function)}：实体不可变（record）→ 框架把基线交给业务给的映射函数，业务只做映射、不做版本判断。</li>
 * </ul>
 *
 * <p>
 * 业务侧用法：{@code aggregatePersistence.save(entity)} 或
 * {@code aggregatePersistence.save(aggregate, version -> toEntity(aggregate, version))}—— 两种形态都不出现 HTTP 协议概念（ETag /
 * lockedVersion），版本对业务是纯数据流。
 */
public class AggregatePersistence {

    private final JdbcAggregateTemplate template;
    private final RelationalMappingContext mappingContext;

    public AggregatePersistence(JdbcAggregateTemplate template, RelationalMappingContext mappingContext) {
        this.template = template;
        this.mappingContext = mappingContext;
    }

    /**
     * 存库（实体版本可写时）：框架取本事务内的加载基线并写入实体版本，随后存库。
     *
     * <p>
     * 乐观锁冲突（CAS 未命中）在此转译为领域异常（并发版本冲突 → 409），业务侧无需捕获技术异常。
     *
     * @param entity
     *            待存实体（版本属性可写）
     * @return 存库后的实体
     */
    public <P> P save(P entity) {
        seedVersion(entity);
        try {
            return template.save(entity);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw concurrentConflict(entity, null, e);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw constraintConflict(entity, e);
        }
    }

    /**
     * 加载并映射（单条）：框架完成「加载 → 映射 → 登记来源行」，业务只提供映射函数。
     *
     * <p>
     * 登记的意义：把该领域聚合实例与它加载时的存储版本关联起来（并发基线随实例隔离）。关联由框架完成——业务不出现任何快照 / 基线概念。
     *
     * @param entity
     *            持久化实体（由 {@code JdbcAggregateTemplate} 加载）
     * @param mapper
     *            实体 → 领域聚合的映射
     * @return 领域聚合（实体为 null → 空）
     */
    public <P, A> java.util.Optional<A> load(P entity, Function<P, A> mapper) {
        if (entity == null) {
            return java.util.Optional.empty();
        }
        A aggregate = mapper.apply(entity);
        if (aggregate != null) {
            AggregateSnapshots.recordLoaded(identityOf(entity), aggregate, versionOf(entity));
        }
        return java.util.Optional.ofNullable(aggregate);
    }

    /**
     * 加载并映射（批量）：逐条映射并登记来源行。
     *
     * @param entities
     *            持久化实体集合
     * @param mapper
     *            实体 → 领域聚合的映射
     * @return 领域聚合列表（保持入参顺序；映射结果为 null 的条目跳过）
     */
    public <P, A> List<A> loadAll(Iterable<P> entities, Function<P, A> mapper) {
        List<A> aggregates = new ArrayList<>();
        for (P entity : entities) {
            load(entity, mapper).ifPresent(aggregates::add);
        }
        return aggregates;
    }

    /**
     * 存库（实体不可变时）：框架取本事务内的加载基线交给映射函数，由业务完成实体构造。
     *
     * <p>
     * 基线以<b>实体身份</b>定位（框架先以空版本构造一次探取身份，再按该身份取基线重建）——业务只做映射，不查版本。
     *
     * @param aggregate
     *            聚合实例（业务对象）
     * @param entityFactory
     *            实体构造：入参为加载基线（null = 新聚合）
     * @return 存库后的实体
     */
    public <A, P> P save(A aggregate, Function<Long, P> entityFactory) {
        P probe = entityFactory.apply(null);
        Object identity = identityOf(probe);
        // 基线按聚合实例取（并发基线随实例隔离）：该实例加载时的快照版本
        Long baseline = AggregateSnapshots.baselineOf(aggregate);
        if (baseline == null) {
            baseline = AggregateSnapshots.firstSnapshotOf(identity);
        }
        P entity = baseline == null ? probe : entityFactory.apply(baseline);
        P saved;
        try {
            saved = template.save(entity);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            throw concurrentConflict(entity, baseline, e);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw constraintConflict(entity, e);
        }
        // 存库成功：该实例的基线推进到新版本（同事务内连续保存时第二次以最新版本做 CAS）
        AggregateSnapshots.recordSaved(identity, aggregate, versionOf(saved));
        return saved;
    }

    /** 数据约束冲突转译：唯一键 / 外键等 → 领域异常（409，统一码见 lib-exception {@code CommonResCode}）。 */
    private RuntimeException constraintConflict(Object entity, RuntimeException cause) {
        return new com.airedgesoft.lib.exception.DomainException(
                com.airedgesoft.lib.exception.CommonResCode.CONSTRAINT_CONFLICT, cause, identityOf(entity));
    }

    /** 并发版本冲突转译：乐观锁 CAS 未命中 → 领域异常（409，统一码见 lib-exception {@code CommonResCode}）。 */
    private RuntimeException concurrentConflict(Object entity, Long expectedVersion, RuntimeException cause) {
        return new com.airedgesoft.lib.exception.DomainException(
                com.airedgesoft.lib.exception.CommonResCode.VERSION_CONFLICT, cause, identityOf(entity),
                expectedVersion);
    }

    /** 存库后实体的版本（无版本列 → null）。 */
    private Long versionOf(Object entity) {
        try {
            RelationalPersistentEntity<?> persistentEntity = mappingContext
                    .getRequiredPersistentEntity(entity.getClass());
            if (!persistentEntity.hasVersionProperty()) {
                return null;
            }
            Object raw = persistentEntity.getPropertyAccessor(entity)
                    .getProperty(persistentEntity.getRequiredVersionProperty());
            return raw == null ? null : Long.valueOf(((Number) raw).longValue());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 表名（实体元数据）：业务拼业务过滤条件 SQL 时取用，无需在实现类里声明表名常量。
     */
    public String tableNameOf(Class<?> entityType) {
        return mappingContext.getRequiredPersistentEntity(entityType).getTableName().getReference();
    }

    /**
     * 主键列名（实体元数据）：业务拼业务过滤条件 SQL 时取用。
     */
    public String idColumnOf(Class<?> entityType) {
        return mappingContext.getRequiredPersistentEntity(entityType).getIdColumn().getReference();
    }

    /** 实体身份（按持久化元数据取 {@code @Id}）。 */
    private Object identityOf(Object entity) {
        try {
            RelationalPersistentEntity<?> persistentEntity = mappingContext
                    .getRequiredPersistentEntity(entity.getClass());
            return persistentEntity.getIdentifierAccessor(entity).getIdentifier();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 版本播种：实体版本为空且本事务加载过同 id 聚合 → 写入加载基线（作为 CAS 基线）。实体不可变或无版本列 → 跳过。
     */
    private void seedVersion(Object entity) {
        if (entity == null) {
            return;
        }
        try {
            RelationalPersistentEntity<?> persistentEntity = mappingContext
                    .getRequiredPersistentEntity(entity.getClass());
            if (!persistentEntity.hasVersionProperty()) {
                return;
            }
            PersistentPropertyAccessor<Object> accessor = persistentEntity.getPropertyAccessor(entity);
            if (accessor.getProperty(persistentEntity.getRequiredVersionProperty()) != null) {
                return;
            }
            Object identity = persistentEntity.getIdentifierAccessor(entity).getIdentifier();
            Long baseline = AggregateSnapshots.firstSnapshotOf(identity);
            if (baseline != null) {
                accessor.setProperty(persistentEntity.getRequiredVersionProperty(), baseline);
            }
        } catch (RuntimeException ignored) {
            // 不可变实体：版本由映射函数注入（save(aggregate, factory) 形态）
        }
    }
}
