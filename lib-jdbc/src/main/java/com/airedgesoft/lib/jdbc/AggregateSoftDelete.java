package com.airedgesoft.lib.jdbc;

import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.common.error.DomainException;
import sk.common.error.PersistenceResultCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Data JDBC 写模型的聚合软删除生命周期操作（软删除 / 恢复 / 物理删除）：<b>表名、主键列、版本列全部取自实体元数据</b> （{@code @Table} / {@code @Id} /
 * {@code @Version}），业务侧无需声明任何列名常量；软删列模型（标志列 / 时间列 / 标志值）由调用方 经 {@link JdbcSoftDelete.Columns} 显式声明（构造期校验）。
 *
 * <p>
 * <b>期望版本守卫</b>：调用方显式传入期望版本时（重载方法），版本谓词与写入在<b>同一条语句</b>内原子完成——陈旧令牌在写入生效前被拒。 删除 /
 * 恢复族端点不携带、不校验版本（并发契约），因此这些操作通常为无条件执行；不存在「已生效却报冲突」。 期望版本的解析（如从请求级上下文读取）属协议桥接职责，由宿主侧装配完成，本类不感知。
 *
 * <p>
 * <b>软删 / 恢复属状态变更</b>：存在版本列时同语句推进 {@code version = version + 1}（对外并发代数随之推进）；0 行归类 —— 目标行存在但版本与期望不符 →
 * 409（并发冲突，统一异常出口）；否则返回 false（不存在 / 状态不符，调用方映射 404）。
 */
public final class AggregateSoftDelete {

    private final JdbcTemplate jdbcTemplate;
    private final MappingContext<? extends RelationalPersistentEntity<?>, ?> mappingContext;
    private final JdbcSoftDelete.Columns columns;

    /**
     * 以数据源模板、实体元数据上下文与软删列模型构造。
     *
     * @param jdbcTemplate
     *            与调用方事务同源的数据源模板
     * @param mappingContext
     *            Spring Data 关系型映射上下文（表名 / 主键列 / 版本列元数据来源）
     * @param columns
     *            软删列模型声明（标志列 / 时间列 / 存活与已删标志值）
     */
    public AggregateSoftDelete(JdbcTemplate jdbcTemplate,
            MappingContext<? extends RelationalPersistentEntity<?>, ?> mappingContext, JdbcSoftDelete.Columns columns) {
        this.jdbcTemplate = java.util.Objects.requireNonNull(jdbcTemplate, "JDBC 模板不能为空");
        this.mappingContext = java.util.Objects.requireNonNull(mappingContext, "映射上下文不能为空");
        this.columns = java.util.Objects.requireNonNull(columns, "列模型声明不能为空");
    }

    /**
     * 无条件软删除（回收站）：存活行 → 置软删标记；已删 / 不存在 → false（调用方映射 404 或幂等 204）。
     *
     * @param entityType
     *            持久化实体类型（元数据来源：表名 / 主键列 / 版本列）
     * @param id
     *            聚合身份
     */
    public <P> boolean softDelete(Class<P> entityType, Object id) {
        return softDelete(entityType, id, null);
    }

    /**
     * 带期望版本守卫的软删除：版本谓词与改写在单条语句内原子完成，0 行且版本不符 → 409。
     */
    public <P> boolean softDelete(Class<P> entityType, Object id, Long expectedVersion) {
        Meta meta = meta(entityType);
        String sql = "UPDATE " + meta.table() + " SET " + columns.flagColumn() + " = ?, " + columns.timeColumn()
                + " = ?" + versionIncrement(meta) + " WHERE " + meta.idColumn() + " = ? AND " + columns.flagColumn()
                + " = " + columns.aliveValue() + versionPredicate(meta, expectedVersion);
        List<Object> parameters = new ArrayList<>();
        parameters.add(columns.deletedValue());
        parameters.add(JdbcSoftDelete.nowMillis());
        parameters.add(id);
        if (expectedVersion != null) {
            parameters.add(expectedVersion);
        }
        if (jdbcTemplate.update(sql, parameters.toArray()) > 0) {
            return true;
        }
        throwIfConcurrentMismatch(meta, id, expectedVersion, false);
        return false;
    }

    /**
     * 无条件恢复（回收站还原）：已删行 → 清除软删标记；未删 / 不存在 → false（调用方映射 404）。
     */
    public <P> boolean restore(Class<P> entityType, Object id) {
        return restore(entityType, id, null);
    }

    /**
     * 带期望版本守卫的恢复。
     */
    public <P> boolean restore(Class<P> entityType, Object id, Long expectedVersion) {
        Meta meta = meta(entityType);
        String sql = "UPDATE " + meta.table() + " SET " + columns.flagColumn() + " = " + columns.aliveValue() + ", "
                + columns.timeColumn() + " = ?" + versionIncrement(meta) + " WHERE " + meta.idColumn() + " = ? AND "
                + columns.flagColumn() + " = " + columns.deletedValue() + versionPredicate(meta, expectedVersion);
        List<Object> parameters = new ArrayList<>();
        parameters.add(JdbcSoftDelete.nowMillis());
        parameters.add(id);
        if (expectedVersion != null) {
            parameters.add(expectedVersion);
        }
        if (jdbcTemplate.update(sql, parameters.toArray()) > 0) {
            return true;
        }
        throwIfConcurrentMismatch(meta, id, expectedVersion, true);
        return false;
    }

    /**
     * 受限物理删除（回收站彻底删除）：仅对已软删行生效（真实 {@code DELETE}）；不存在或未软删 → false（调用方映射 404）。
     */
    public <P> boolean expunge(Class<P> entityType, Object id) {
        return expunge(entityType, id, null);
    }

    /**
     * 带期望版本守卫的物理删除。
     */
    public <P> boolean expunge(Class<P> entityType, Object id, Long expectedVersion) {
        Meta meta = meta(entityType);
        String sql = "DELETE FROM " + meta.table() + " WHERE " + meta.idColumn() + " = ? AND " + columns.flagColumn()
                + " = " + columns.deletedValue() + versionPredicate(meta, expectedVersion);
        List<Object> parameters = new ArrayList<>();
        parameters.add(id);
        if (expectedVersion != null) {
            parameters.add(expectedVersion);
        }
        if (jdbcTemplate.update(sql, parameters.toArray()) > 0) {
            return true;
        }
        throwIfConcurrentMismatch(meta, id, expectedVersion, true);
        return false;
    }

    /** 实体元数据（表名 / 主键列 / 版本列）。 */
    private Meta meta(Class<?> entityType) {
        RelationalPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(entityType);
        String versionColumn = null;
        if (entity.hasVersionProperty()) {
            RelationalPersistentProperty version = entity.getRequiredVersionProperty();
            versionColumn = version.getColumnName().getReference();
        }
        return new Meta(entity.getTableName().getReference(), entity.getIdColumn().getReference(), versionColumn);
    }

    /**
     * 版本推进片段：软删除 / 恢复属于状态变更，存在版本列时同步 {@code version = version + 1}；无版本列时为空。
     */
    private static String versionIncrement(Meta meta) {
        return meta.versionColumn() == null ? "" : ", " + meta.versionColumn() + " = " + meta.versionColumn() + " + 1";
    }

    /**
     * 版本谓词片段：<b>仅在存在期望版本时附加</b>——SQL 谓词与绑定参数必须同步，否则参数个数不匹配。
     */
    private static String versionPredicate(Meta meta, Long expectedVersion) {
        return expectedVersion == null || meta.versionColumn() == null ? "" : " AND " + meta.versionColumn() + " = ?";
    }

    /**
     * 0 行归类：目标行存在（软删 / 已删）但版本与期望不符 → 409（并发版本冲突）；否则静默返回，由调用方按 false 映射 404。
     *
     * @param deletedScope
     *            true = 目标行处于「已软删」状态（恢复 / 物理删除场景），false = 存活行（软删场景）
     */
    private void throwIfConcurrentMismatch(Meta meta, Object id, Long expected, boolean deletedScope) {
        if (expected == null || meta.versionColumn() == null) {
            return;
        }
        String scope = deletedScope
                ? " AND " + columns.flagColumn() + " = " + columns.deletedValue()
                : " AND " + columns.flagColumn() + " = " + columns.aliveValue();
        List<Long> versions = jdbcTemplate.queryForList("SELECT " + meta.versionColumn() + " FROM " + meta.table()
                + " WHERE " + meta.idColumn() + " = ?" + scope, Long.class, id);
        if (!versions.isEmpty() && !versions.get(0).equals(expected)) {
            throw new DomainException(PersistenceResultCode.VERSION_CONFLICT, id, expected);
        }
    }

    /** 实体元数据快照。 */
    private record Meta(String table, String idColumn, String versionColumn) {
    }
}
