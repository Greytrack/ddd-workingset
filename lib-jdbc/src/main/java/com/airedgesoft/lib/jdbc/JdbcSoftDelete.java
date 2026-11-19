package com.airedgesoft.lib.jdbc;

import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 软删除写路径的语句构建与执行工具：软删 / 恢复 / 物理删除 / 删除时刻探测均为<b>仓储显式调用</b>的受控语句， 不存在任何 SQL 自动改写通道——删除语义是领域决策，由仓储语句明说，本类只提供列模型安全的语句积木。
 *
 * <p>
 * <b>列模型由调用方声明</b>（{@link Columns}，构造期校验标识符防注入）：删除标志列 / 时间列 / 存活与已删标志值。 配置口径（列名、标志值）由宿主从统一配置读取后构造声明传入，本类不感知任何配置键。
 *
 * <p>
 * <b>语句语义</b>（列模型沿用既有表 {@code delete_flag} 0 存活 / 1 已删 + {@code updated_time} 毫秒时）：
 * <ul>
 * <li>{@link #softDeleteById}：{@code UPDATE t SET delete_flag = 1, updated_time = ? WHERE pk = ? AND delete_flag = 0}，
 * 返回受影响行数（0 = 资源不存在或已删除，按幂等 204 语义静默成功）；可带乐观版本谓词（版本比对与改写同语句原子完成）； <b>不推进版本列</b>——软删 / 恢复属状态变更，带版本列的聚合由仓储在同一条语句显式追加
 * {@code version = version + 1} （demo {@code DemoTaskRepositoryImpl} 为权威示例）；</li>
 * <li>{@link #softDeleteByIds}：批量形态（主键升序执行，兼容 {@code DeadlockSafeExecutor} 的加锁次序约定）；</li>
 * <li>{@link #restoreById}：恢复（标志回写存活值 + 刷新时间列；仅对已删行生效）；</li>
 * <li>{@link #expungeById}：物理删除（真实 {@code DELETE}，仅对已删行生效——回收站彻底删除，绕过任何软删通道）；</li>
 * <li>{@link #deletedAtOf}：回收站 Get 标注删除时刻（取已删行的时间列；存活 / 不存在 → null）；</li>
 * <li>{@link #aliveCondition}：{@code (delete_flag = 0)} 存活谓词片段，拼进仓储查询 WHERE。</li>
 * </ul>
 *
 * <p>
 * {@code updated_by} 等操作者列由 BC 仓储自行维护（本类不感知认证上下文）。读取侧的存活过滤注入与请求级放行 属查询通道关注点，不在本类职责内。
 */
public final class JdbcSoftDelete {

    /** 软删除列模型声明：列名 / 标志值由调用方统一声明，构造期校验。 */
    public record Columns(String flagColumn, String timeColumn, int aliveValue, int deletedValue) {

        // JDBC 参数不能绑定列名，标识符只接受受限格式，拒绝 SQL 片段。
        private static final String IDENTIFIER_PATTERN = "[A-Za-z][A-Za-z0-9_]*";

        public Columns {
            identifier(flagColumn, "flagColumn");
            identifier(timeColumn, "timeColumn");
        }

        private static void identifier(String name, String field) {
            if (name == null || !name.matches(IDENTIFIER_PATTERN)) {
                throw new IllegalArgumentException("软删除列名非法（应为标识符）: " + field + " = " + name);
            }
        }
    }

    private final Columns columns;

    /**
     * 以声明的列模型构造软删语句工具。
     *
     * @param columns
     *            删除标志列 / 时间列 / 存活与已删标志值声明；列名构造期校验，非法即拒绝
     */
    public JdbcSoftDelete(Columns columns) {
        this.columns = java.util.Objects.requireNonNull(columns, "列模型声明不能为空");
    }

    /** 「未删除」行过滤条件（可带表别名）：{@code (t.delete_flag = 0)}。 */
    public String aliveCondition(String tableAlias) {
        return "(" + (tableAlias == null || tableAlias.isBlank() ? "" : tableAlias.trim() + ".") + columns.flagColumn()
                + " = " + columns.aliveValue() + ")";
    }

    /** 当前 UTC 毫秒时刻（时间列通用取值：建行审计初值与软删 / 恢复同源；已删行的该值即删除时刻）。 */
    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 软删除单条：置标志 + 刷新时间列 + 存活行守卫。返回受影响行数（0 = 不存在或已删除——DELETE 幂等 204 语义，调用方无需报错）。
     *
     * @param jdbcTemplate
     *            与调用方事务同源的数据源模板
     * @param table
     *            目标表名
     * @param pkColumn
     *            主键列名
     * @param id
     *            聚合身份
     */
    public int softDeleteById(JdbcTemplate jdbcTemplate, String table, String pkColumn, Object id) {
        String sql = "UPDATE " + table + " SET " + columns.flagColumn() + " = ?, " + columns.timeColumn()
                + " = ? WHERE " + pkColumn + " = ? AND " + columns.flagColumn() + " = " + columns.aliveValue();
        return jdbcTemplate.update(sql, columns.deletedValue(), nowMillis(), id);
    }

    /**
     * 软删除单条（可带乐观版本谓词）：{@code versionColumn} 与 {@code expectedVersion} 均非空时谓词追加 {@code AND version = ?}——版本比对与标志改写在单条
     * UPDATE 内原子完成，无先读后判（check-then-act）竞争窗口。 返回受影响行数（0 = 不存在 / 已删除 / 版本不符，语义归类由调用方完成）。
     *
     * @param versionColumn
     *            乐观版本列名；空则退化为无条件软删
     * @param expectedVersion
     *            期望版本；null 或版本列为空时谓词不生效
     */
    public int softDeleteById(JdbcTemplate jdbcTemplate, String table, String pkColumn, Object id, String versionColumn,
            Long expectedVersion) {
        String sql = "UPDATE " + table + " SET " + columns.flagColumn() + " = ?, " + columns.timeColumn()
                + " = ? WHERE " + pkColumn + " = ? AND " + columns.flagColumn() + " = " + columns.aliveValue();
        if (expectedVersion != null && versionColumn != null && !versionColumn.isBlank()) {
            sql += " AND " + versionColumn + " = ?";
            return jdbcTemplate.update(sql, columns.deletedValue(), nowMillis(), id, expectedVersion);
        }
        return jdbcTemplate.update(sql, columns.deletedValue(), nowMillis(), id);
    }

    /**
     * 批量软删除：按主键升序逐条执行（加锁次序确定，防多行更新死锁）；返回受影响总行数。
     *
     * @param ids
     *            主键集合；空集合不执行任何语句
     */
    public int softDeleteByIds(JdbcTemplate jdbcTemplate, String table, String pkColumn, List<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String sql = "UPDATE " + table + " SET " + columns.flagColumn() + " = ?, " + columns.timeColumn()
                + " = ? WHERE " + pkColumn + " = ? AND " + columns.flagColumn() + " = " + columns.aliveValue();
        int affected = 0;
        List<?> sorted = ids.stream().sorted().toList();
        for (Object id : sorted) {
            affected += jdbcTemplate.update(sql, columns.deletedValue(), nowMillis(), id);
        }
        return affected;
    }

    /**
     * 恢复（回收站还原）：标志回写存活值 + 刷新时间列；仅对已删行生效。返回受影响行数（0 = 资源不存在或未删除）。
     */
    public int restoreById(JdbcTemplate jdbcTemplate, String table, String pkColumn, Object id) {
        String sql = "UPDATE " + table + " SET " + columns.flagColumn() + " = " + columns.aliveValue() + ", "
                + columns.timeColumn() + " = ? WHERE " + pkColumn + " = ? AND " + columns.flagColumn() + " = "
                + columns.deletedValue();
        return jdbcTemplate.update(sql, nowMillis(), id);
    }

    /**
     * 物理删除（回收站彻底删除）：真实 {@code DELETE}，仅对已软删行生效；不存在或未软删 → 0 行。 从属行清理由调用方自行执行（本类不感知聚合子表）。
     */
    public int expungeById(JdbcTemplate jdbcTemplate, String table, String pkColumn, Object id) {
        String sql = "DELETE FROM " + table + " WHERE " + pkColumn + " = ? AND " + columns.flagColumn() + " = "
                + columns.deletedValue();
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 按主键取删除时刻（回收站 Get 标注 deleteTime 用）：已删行返回其时间列毫秒值；存活 / 不存在 → null。
     */
    public Long deletedAtOf(JdbcTemplate jdbcTemplate, String table, String pkColumn, Object id) {
        try {
            return jdbcTemplate.queryForObject("SELECT " + columns.timeColumn() + " FROM " + table + " WHERE "
                    + pkColumn + " = ? AND " + columns.flagColumn() + " = " + columns.deletedValue(), Long.class, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }
}
