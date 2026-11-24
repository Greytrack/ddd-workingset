package com.airedgesoft.lib.restful.softdelete;

import java.util.Locale;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 软删除 SQL 改写器（<b>读模型侧</b>，基于 MyBatis-Plus 内嵌的 JSqlParser；v4-restful-api.md §5.1）。
 *
 * <p>
 * <b>列模型沿用 v3 既有表</b>：{@code delete_flag}（0 存活 / 1 已删）+ {@code updated_time}（毫秒）。SELECT 顶层 WHERE 注入
 * {@code delete_flag = 0}（原 WHERE 含 OR 时加括号保语义；UNION 各分支分别注入；WHERE 内子查询不递归注入）； <b>含 JOIN
 * 时按主表别名限定列名</b>（避免多表同名列歧义）；WHERE 已自管标志列条件（回收站视图）→ 不注入并告警。
 *
 * <p>
 * <b>写语句（DELETE / UPDATE / INSERT）一律原样放行</b>：软删 / 恢复 / 彻底删除是领域生命周期决策，由应用层 → 仓储显式语句执行 （lib-jdbc {@code JdbcSoftDelete} /
 * {@code AggregateSoftDelete}；demo {@code DemoTaskRepositoryImpl} 为权威示例）， 框架只统一读侧存活过滤，不做任何写语句自动改写。
 *
 * <p>
 * <b>作用域</b>：只改写 {@link SoftDeleteContext#tables()} 白名单内主表的语句（白名单为空 = 不限定，仅工具直调语义；拦截器装配要求 {@code enabled=true}
 * 时必填白名单）。{@code ?showDeleted=true} 放行由 {@link SoftDeleteContext#deletedIncluded()} 判定（调用方裁决，本类不感知请求）。
 */
public final class SoftDeleteSqlRewriter {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteSqlRewriter.class);

    private SoftDeleteSqlRewriter() {
    }

    /**
     * 改写一条 SQL。
     *
     * @param sql
     *            原始 SQL（MyBatis 生成的静态形态，{@code #{} 参数已保留为 {@code ?} 占位符，JSqlParser 可解析） @param includeDeleted
     *            是否放行已删行（true = 原样返回） @return 改写后的 SQL；非 SELECT 语句、作用域外表、WHERE 自管条件的语句原样返回
     */
    public static String rewrite(String sql, boolean includeDeleted) {
        if (includeDeleted || sql == null || sql.isBlank()) {
            return sql;
        }
        Statement statement;
        try {
            statement = net.sf.jsqlparser.parser.CCJSqlParserUtil.parseStatements(sql).get(0);
        } catch (net.sf.jsqlparser.JSQLParserException e) {
            // MyBatis 生成的静态 SQL 解析失败 = 不支持的语句形态（配置错误不静默放行——放行即丢过滤条件）
            throw new IllegalStateException("软删除 SQL 改写失败（不支持的语句形态）: " + sql, e);
        }
        if (statement instanceof Select select) {
            return applySelect(select) ? select.toString() : sql;
        }
        return sql;
    }

    private static boolean applySelect(Select select) {
        if (select instanceof PlainSelect plainSelect) {
            return injectAlive(plainSelect);
        }
        if (select instanceof SetOperationList setOperationList) {
            boolean changed = false;
            for (Select branch : setOperationList.getSelects()) {
                changed |= applySelect(branch);
            }
            return changed;
        }
        return false;
    }

    /** 注入存活守卫；返回是否发生改写（作用域外 / WHERE 自管条件 → false）。 */
    private static boolean injectAlive(PlainSelect plainSelect) {
        FromItem fromItem = plainSelect.getFromItem();
        String prefix = "";
        if (fromItem instanceof Table table) {
            if (!SoftDeleteContext.inScope(table.getFullyQualifiedName())) {
                log.debug("软删除改写跳过：表不在软删白名单内 {}", table.getFullyQualifiedName());
                return false;
            }
            if (plainSelect.getJoins() != null && !plainSelect.getJoins().isEmpty()) {
                // JOIN 下多表可能同名标志列 → 以主表别名限定，只过滤主表（连接表是否过滤由其自身语句裁决）
                prefix = (table.getAlias() != null ? table.getAlias().getName() : table.getName()) + ".";
            }
        } else if (!SoftDeleteContext.tables().isEmpty()) {
            // FROM 为派生表 / 子查询：无法判定主表是否具备软删列 → 不注入（宁可不改写，也不改写无该列的语句）
            log.debug("软删除改写跳过：FROM 非单表且已配置软删白名单，无法判定作用域");
            return false;
        }
        if (selfManaged(plainSelect.getWhere())) {
            log.warn("软删除改写跳过存活过滤注入：WHERE 已自管标志列 {}，请核对该语句条件的正确性: {}", SoftDeleteContext.flagColumn(), plainSelect);
            return false;
        }
        plainSelect.setWhere(andAlive(plainSelect.getWhere(), prefix));
        return true;
    }

    /**
     * WHERE 是否已自管标志列<b>条件</b>（回收站视图 / 业务自管过滤）——按 AST 列引用判定，而非 SQL 文本子串： {@code WHERE title = 'delete_flag'}
     * 这类把列名当字符串字面量的条件不算自管，仍须注入存活过滤（否则已删行泄漏）。 遍历覆盖二元 / 括号 / IN 列表 / CASE 等表达式与 WHERE 内子查询；SELECT 列表投影引用该列不触发跳过。
     */
    private static boolean selfManaged(Expression where) {
        if (where == null) {
            return false;
        }
        String flag = SoftDeleteContext.flagColumn().toLowerCase(Locale.ROOT);
        boolean[] found = {false};
        ExpressionVisitorAdapter<Void> columns = new ExpressionVisitorAdapter<>() {
            @Override
            public <S> Void visit(Column column, S context) {
                String name = column.getColumnName();
                if (name != null && name.replace("\"", "").toLowerCase(Locale.ROOT).equals(flag)) {
                    found[0] = true;
                }
                return null;
            }
        };
        columns.setSelectVisitor(new SelectVisitorAdapter<>() {
            @Override
            public <S> Void visit(PlainSelect plainSelect, S context) {
                if (plainSelect.getWhere() != null) {
                    plainSelect.getWhere().accept(columns, context);
                }
                return null;
            }

            @Override
            public <S> Void visit(SetOperationList setOperationList, S context) {
                for (Select branch : setOperationList.getSelects()) {
                    branch.accept(this, context);
                }
                return null;
            }
        });
        where.accept(columns, null);
        return found[0];
    }

    /** {@code delete_flag = 0} AND (原 WHERE)；原 WHERE 为 null 时仅存活守卫。 */
    private static Expression andAlive(Expression originalWhere, String prefix) {
        EqualsTo alive = new EqualsTo(new Column(prefix + SoftDeleteContext.flagColumn()),
                new LongValue(SoftDeleteContext.aliveValue()));
        return originalWhere == null ? alive : new AndExpression(alive, new ParenthesedExpressionList(originalWhere));
    }
}
