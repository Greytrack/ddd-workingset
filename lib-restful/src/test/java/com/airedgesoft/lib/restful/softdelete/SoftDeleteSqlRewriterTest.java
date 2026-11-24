package com.airedgesoft.lib.restful.softdelete;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 软删除 SQL 改写器单测（读模型侧；v3 列模型 delete_flag + updated_time：SELECT 注入存活条件 / 放行与 WHERE 自管守卫 / 表级白名单作用域 / JOIN 别名限定 / 写语句放行）。
 */
class SoftDeleteSqlRewriterTest {

    @BeforeAll
    static void configureDefaults() {
        SoftDeleteContext.configure(new SoftDeleteProperties());
    }

    @Test
    void select_simpleWhere_injectsAliveCondition() {
        String rewritten = SoftDeleteSqlRewriter.rewrite("SELECT id, title FROM demo_task WHERE project_id = ?", false);
        assertThat(rewritten)
                .isEqualToIgnoringCase("SELECT id, title FROM demo_task WHERE delete_flag = 0 AND (project_id = ?)");
    }

    @Test
    void select_noWhere_injectsAliveOnly() {
        String rewritten = SoftDeleteSqlRewriter.rewrite("SELECT id FROM demo_task", false);
        assertThat(rewritten).containsIgnoringCase("WHERE delete_flag = 0");
    }

    @Test
    void select_orCondition_wrapsOriginalWhere() {
        String rewritten = SoftDeleteSqlRewriter.rewrite("SELECT id FROM demo_task WHERE status = 'A' OR status = 'B'",
                false);
        // 原含 OR 的 WHERE 必须被括号保护，否则语义漂移为「存活 OR status=B」
        assertThat(rewritten).containsIgnoringCase("delete_flag = 0 AND (status = 'A' OR status = 'B')");
    }

    @Test
    void select_union_injectsPerBranch() {
        String rewritten = SoftDeleteSqlRewriter.rewrite("SELECT id FROM a UNION SELECT id FROM b", false);
        assertThat(countOccurrences(rewritten.toLowerCase(), "delete_flag = 0")).isEqualTo(2);
    }

    @Test
    void select_join_qualifiesFlagWithPrimaryAlias() {
        String sql = "SELECT u.id, f.name FROM demo_task_write u LEFT JOIN demo_project_write f ON f.id = u.project_id"
                + " WHERE u.status = ?";
        String rewritten = SoftDeleteSqlRewriter.rewrite(sql, false);
        // 两表同有 delete_flag → 必须以主表别名限定，否则列名歧义
        assertThat(rewritten).containsIgnoringCase("u.delete_flag = 0");
        assertThat(rewritten).doesNotContain("f.delete_flag");
    }

    @Test
    void select_flagInProjectionOnly_stillInjects() {
        // SELECT 列表引用标志列（回收站投影 deleteTime）不构成「WHERE 自管条件」，仍须注入存活过滤
        String rewritten = SoftDeleteSqlRewriter
                .rewrite("SELECT id, delete_flag, updated_time FROM demo_task WHERE id = ?", false);
        assertThat(rewritten).containsIgnoringCase("WHERE delete_flag = 0 AND (id = ?)");
    }

    @Test
    void select_whereSelfManaged_skipsInjection() {
        // 回收站自管条件（如 delete_flag = 1）——重复注入会产出空集
        String sql = "SELECT id FROM demo_task WHERE delete_flag = 1";
        assertThat(SoftDeleteSqlRewriter.rewrite(sql, false)).isEqualTo(sql);
    }

    @Test
    void scopedTables_onlyRewritesWhitelistedPrimaryTable() {
        SoftDeleteProperties properties = new SoftDeleteProperties();
        properties.setTables(List.of("demo_task_write"));
        SoftDeleteContext.configure(properties);
        try {
            String legacy = "SELECT id FROM air_inst_work_hours WHERE id = ?";
            assertThat(SoftDeleteSqlRewriter.rewrite(legacy, false)).isEqualTo(legacy);
            assertThat(SoftDeleteSqlRewriter.rewrite("SELECT id FROM demo_task_write", false))
                    .containsIgnoringCase("delete_flag = 0");
        } finally {
            SoftDeleteContext.configure(new SoftDeleteProperties());
        }
    }

    @Test
    void scopedTables_schemaQualifiedNameMatches() {
        SoftDeleteProperties properties = new SoftDeleteProperties();
        properties.setTables(List.of("demo_task_write"));
        SoftDeleteContext.configure(properties);
        try {
            assertThat(SoftDeleteSqlRewriter.rewrite("SELECT id FROM public.demo_task_write", false))
                    .containsIgnoringCase("delete_flag = 0");
        } finally {
            SoftDeleteContext.configure(new SoftDeleteProperties());
        }
    }

    @Test
    void includeDeleted_bypassesRewrite() {
        String sql = "SELECT id FROM demo_task WHERE status = 'A'";
        assertThat(SoftDeleteSqlRewriter.rewrite(sql, true)).isEqualTo(sql);
    }

    @Test
    void writeStatementsPassThrough() {
        // 写语句一律不改写：软删 / 恢复 / 彻底删除由仓储显式语句执行（lib-jdbc JdbcSoftDelete / AggregateSoftDelete）
        String deleteSql = "DELETE FROM demo_task WHERE id = ?";
        assertThat(SoftDeleteSqlRewriter.rewrite(deleteSql, false)).isEqualTo(deleteSql);
        String updateSql = "UPDATE demo_task SET title = ? WHERE id = ?";
        assertThat(SoftDeleteSqlRewriter.rewrite(updateSql, false)).isEqualTo(updateSql);
    }

    @Test
    void customColumnsAndFlagValues_applied() {
        SoftDeleteProperties properties = new SoftDeleteProperties();
        properties.setFlagColumn("del_flag");
        properties.setTimeColumn("gmt_modified");
        properties.setAliveValue(0);
        properties.setDeletedValue(2);
        SoftDeleteContext.configure(properties);
        try {
            assertThat(SoftDeleteSqlRewriter.rewrite("SELECT id FROM t", false)).containsIgnoringCase("del_flag = 0");
        } finally {
            SoftDeleteContext.configure(new SoftDeleteProperties());
        }
    }

    @Test
    void select_flagNameAsStringLiteral_stillInjects() {
        // 列名出现在字符串字面量里不是「自管条件」——子串匹配会误判跳过，导致已删行泄漏（P0 回归）
        String rewritten = SoftDeleteSqlRewriter.rewrite("SELECT id FROM demo_task WHERE title = 'delete_flag'", false);
        assertThat(rewritten).containsIgnoringCase("delete_flag = 0 AND (title = 'delete_flag')");
    }

    @Test
    void select_flagColumnInsideSubquery_skipsInjection() {
        String sql = "SELECT id FROM demo_task WHERE id IN (SELECT task_id FROM demo_step WHERE delete_flag = 1)";
        assertThat(SoftDeleteSqlRewriter.rewrite(sql, false)).isEqualTo(sql);
    }

    @Test
    void select_flagColumnInList_skipsInjection() {
        String sql = "SELECT id FROM demo_task WHERE delete_flag IN (0, 1)";
        assertThat(SoftDeleteSqlRewriter.rewrite(sql, false)).isEqualTo(sql);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
