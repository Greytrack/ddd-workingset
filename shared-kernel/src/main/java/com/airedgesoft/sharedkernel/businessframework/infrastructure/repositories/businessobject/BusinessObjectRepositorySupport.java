package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.airedgesoft.lib.exception.CommonResCode;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.lib.jdbc.AggregateMapping;
import com.airedgesoft.lib.jdbc.AggregateMapping.TableMapping;
import com.airedgesoft.lib.jdbc.AggregateRows;
import com.airedgesoft.lib.jdbc.JdbcAggregateStore;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Change;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Clone;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Delete;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Insert;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Operation;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Target;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Value;
import com.airedgesoft.lib.jdbc.JdbcWritePlanner.Changes;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessObject;

import lombok.extern.slf4j.Slf4j;

/**
 * 业务对象写侧仓储支撑（v3 共表存储骨架）：把「与具体 BC 无关」的根级写侧机制收在一处——机制在 {@code lib-jdbc}，编排在基类，
 * <b>存储口径与列声明留在各 BC 子类</b>。
 *
 * <p>
 * <b>基类负责</b>：① 持有 {@link JdbcAggregateStore} 与唯一的 {@link AggregateMapping} 实例（Store 以映射实例引用隔离事务内身份记录，
 * 因此映射必须复用，不能按次重建）；② {@code save} 时序（写计划 → 回执判定 → common 冗余列 upsert → 子类附加写入 → 回执日志）；
 * ③ 根版本核验与锁版本回读；④ common 行身份（{@code common_id}）分配口径。
 *
 * <p>
 * <b>基类不负责</b>：表列声明（{@link #rootTable()}）、common 行业务列取值（{@link #commonRowOf}）、异常转译（子类包装
 * {@link DataIntegrityViolationException}）、索引与富文本等 BC 附加写入（{@link #afterApplied}）。
 *
 * <p>
 * <b>构造约定</b>：依赖全部由子类构造器显式传入（基类不带 Spring 注解，{@code @Repository} 留在子类）；子类必须在<b>自己的构造器末尾</b>
 * 调用 {@link #initialize()}——基类构造器不调用抽象方法，映射只在初始化后构建一次。
 *
 * <p>
 * <b>操作人</b>：{@code operatorId} 经 {@link AggregateMapping.HookContext} 随单次调用进入写计划（审计列），基类不解释其含义，
 * 也不使用线程变量。
 *
 * @param <A>
 *            业务对象聚合类型
 * @param <ID>
 *            聚合身份类型
 */
@Slf4j
public abstract class BusinessObjectRepositorySupport<A extends BusinessObject, ID> {

    /**
     * v3 兼容账号（无请求上下文时的行为人兜底；与 v3 {@code RequestThreadHolder} 缺省口径一致）。
     */
    public static final String NO_AUTH_ACTOR_ID = "419436436619264";

    /** 默认工作集 id（v3 {@code IWorkingset.DEFAULT_WORKINGSET_ID}；工作集 BC 落地后由子类覆写取数源）。 */
    private static final long DEFAULT_WORKINGSET_ID = 1L;

    private final JdbcTemplate jdbcTemplate;

    private final JdbcCommonInstanceStore commonInstances;

    private final JdbcAggregateStore store;

    /** 本仓储唯一的映射实例：查询与保存共用，Store 依此隔离事务内身份与保存状态。 */
    private AggregateMapping<A, ID> mapping;

    protected BusinessObjectRepositorySupport(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
            JdbcCommonInstanceStore commonInstances) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.commonInstances = Objects.requireNonNull(commonInstances, "commonInstances");
        // 事务管理器只用于让 Store 参与外层事务；事务边界由应用服务决定。
        this.store = new JdbcAggregateStore(transactionManager, jdbcTemplate);
    }

    // ==================== 子类必须声明 ====================

    /**
     * 根表声明（表名 + 全列类型 + 身份列 + 锁版本列）。
     *
     * <p>
     * 由各 BC 自己的列声明 SSOT 组装：v3 共表列与业务列都在这里给出，基类只按声明读写，不猜列名。
     */
    protected abstract TableMapping rootTable();

    /**
     * common 行（v3 {@code air_inst_common}）冗余列组装：业务列语义（名称/状态/归属/版次指针等）只有 BC 知道，由子类取值。
     *
     * @param aggregate
     *            已完成写入的聚合终态
     * @param now
     *            本次保存的审计时间戳（与聚合写入同一时刻）
     */
    protected abstract AirInstCommonPo commonRowOf(A aggregate, long now);

    /**
     * 装配本仓储的聚合映射（根表声明、加载与写计划钩子）。
     *
     * <p>
     * 只被 {@link #initialize()} 调用一次；实现里可以用 {@code this::} 引用实例方法。
     */
    protected abstract AggregateMapping<A, ID> createMapping();

    // ==================== 骨架 ====================

    /**
     * 构建唯一映射实例；必须由子类构造器末尾调用。
     */
    protected final void initialize() {
        this.mapping = Objects.requireNonNull(createMapping(), "聚合映射不能为空");
    }

    /** 本仓储的映射实例（{@link #initialize()} 之后可用）。 */
    protected final AggregateMapping<A, ID> mapping() {
        AggregateMapping<A, ID> configured = mapping;
        if (configured == null) {
            throw new IllegalStateException("子类构造器未调用 initialize()，聚合映射尚未装配");
        }
        return configured;
    }

    protected final JdbcAggregateStore store() {
        return store;
    }

    protected final JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    // ==================== 写侧模板 ====================

    /**
     * 保存聚合终态（时序固定，子类不可改编排）：
     * <ol>
     * <li>回执为 {@link JdbcAggregateStore.Receipt.Applied} 才补写 common 冗余列（{@code Unchanged} 未产生 DML，一律跳过）；</li>
     * <li>子类附加写入（{@link #afterApplied}）；</li>
     * <li>回执落 DEBUG 日志（与 {@code lib-jdbc} 的执行日志成对）。</li>
     * </ol>
     *
     * @param aggregate
     *            已执行领域动作的聚合终态
     * @param operatorId
     *            本次保存的操作人；无请求上下文传 {@code null}
     * @return 事务内执行回执（不代表外层事务已提交）
     */
    protected final JdbcAggregateStore.Receipt saveAggregate(A aggregate, Long operatorId) {
        JdbcAggregateStore.Receipt receipt = store.save(mapping(), aggregate,
                new AggregateMapping.HookContext(operatorId));
        if (receipt instanceof JdbcAggregateStore.Receipt.Applied applied) {
            long now = System.currentTimeMillis();
            // common 冗余列与聚合行同属一次保存，但不是聚合列，故不入写入计划（v3 getCommonSql 口径由子类组装）。
            commonInstances.save(commonRowOf(aggregate, now));
            afterApplied(aggregate, applied, operatorId, now);
        }
        log.debug("{}保存完成：对象={} {}", getClass().getSimpleName(), aggregate.getId(), describeReceipt(receipt));
        return receipt;
    }

    /**
     * 实际写入后的附加落库（不属聚合列的共表：索引、富文本等）；默认无附加写入。
     *
     * @param aggregate
     *            聚合终态
     * @param applied
     *            执行回执（含创建/更新口径）
     * @param operatorId
     *            本次保存的操作人（可空）
     * @param now
     *            与本次保存同一时刻的审计时间戳
     */
    protected void afterApplied(A aggregate, JdbcAggregateStore.Receipt.Applied applied, Long operatorId, long now) {
        // 默认无附加写入
    }

    /**
     * common 行身份（v3 共表 {@code common_id}）：显式携带优先；缺省时默认工作集与对象同 id（创建分配口径），否则按同规则即时分配。
     */
    protected long commonIdOf(A aggregate) {
        if (aggregate.getCommonId() != null) {
            return aggregate.getCommonId();
        }
        Long workingsetId = aggregate.getWorkingsetId();
        return workingsetId == null || workingsetId == defaultWorkingsetId()
                ? aggregate.getId()
                : Ids.nextId();
    }

    /** 默认工作集 id（同一空间内多工作集的 BC 覆写）。 */
    protected long defaultWorkingsetId() {
        return DEFAULT_WORKINGSET_ID;
    }

    // ==================== 根版本核验 ====================

    /**
     * 回读根锁版本并与快照比对：根与子行分批读取，期间任何遵循根版本协议的写入都会被发现。
     *
     * @param stored
     *            本次读库的原始快照（取身份与快照锁版本）
     */
    protected final void verifyRootVersion(AggregateRows stored) {
        TableMapping table = rootTable();
        String sql = "SELECT " + table.versionColumn() + " FROM " + table.table().sqlName() + " WHERE "
                + table.identityColumns().get(0) + " = ?";
        Long version = jdbc().queryForObject(sql, Long.class, identityValue(stored));
        if (!Objects.equals(version, storedVersion(stored))) {
            throw new DomainException(CommonResCode.VERSION_CONFLICT, identityValue(stored), storedVersion(stored));
        }
    }

    /** 当前锁版本回读（不存在返回空）；ETag 与并发守卫读同一列。 */
    protected final Optional<Long> rootLockVersion(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        TableMapping table = rootTable();
        // v3 共表身份统一是 BIGINT；泛型身份按 Number 取 long 参与过滤。
        List<Long> ids = List.of(((Number) id).longValue());
        return store.findAll(table, table.identityColumns().get(0), ids).stream().findFirst()
                .map(row -> row.longValue(table.versionColumn()));
    }

    /** 回执的可读描述：口径、根锁版本与写入行数；无变化时明确说明未产生 DML。 */
    protected static String describeReceipt(JdbcAggregateStore.Receipt receipt) {
        if (receipt instanceof JdbcAggregateStore.Receipt.Applied applied) {
            return "口径=" + (applied.kind() == JdbcAggregateStore.Receipt.Kind.CREATED ? "新增" : "更新") + " 根锁版本="
                    + applied.rootVersion() + " 写入行数=" + applied.affectedRows();
        }
        return "字段无净变化（未产生 DML）";
    }

    /** 行为人列值：操作人优先，无请求上下文退回 v3 兼容账号。 */
    protected static String actorOf(Long operatorId) {
        return operatorId == null ? NO_AUTH_ACTOR_ID : String.valueOf(operatorId);
    }


    private Object identityValue(AggregateRows stored) {
        Long identity = stored.root().longValue(rootTable().identityColumns().get(0));
        return identity;
    }

    private Long storedVersion(AggregateRows stored) {
        return stored.root().longValue(rootTable().versionColumn());
    }
}
