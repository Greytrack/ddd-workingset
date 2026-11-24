package com.airedgesoft.lib.restful.softdelete;

import com.airedgesoft.lib.restful.softdelete.mybatis.SoftDeleteInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 软删除 MyBatis-Plus 拦截器单测（组件 2 织入壳；真实 MappedStatement / BoundSql，不起容器）。
 */
class SoftDeleteInnerInterceptorTest {

    private final SoftDeleteInnerInterceptor interceptor = new SoftDeleteInnerInterceptor();

    @BeforeAll
    static void configureDefaults() {
        SoftDeleteContext.configure(new SoftDeleteProperties());
    }

    @Test
    void beforeQuery_rewritesBoundSql() {
        BoundSql boundSql = boundSql("SELECT id FROM demo_task WHERE project_id = ?", SqlCommandType.SELECT);
        interceptor.beforeQuery(null, null, null, RowBounds.DEFAULT, null, boundSql);
        assertThat(boundSql.getSql()).containsIgnoringCase("delete_flag = 0 AND (project_id = ?)");
    }

    @Test
    void beforeQuery_showDeleted_keepsOriginalSql() {
        BoundSql boundSql = boundSql("SELECT id FROM demo_task", SqlCommandType.SELECT);
        SoftDeleteContext.withDeletedIncluded(
                () -> interceptor.beforeQuery(null, null, null, RowBounds.DEFAULT, null, boundSql));
        assertThat(boundSql.getSql()).doesNotContain("delete_flag");
    }

    @Test
    void isMybatisPlusInnerInterceptor() {
        assertThat(interceptor).isInstanceOf(InnerInterceptor.class);
    }

    private static BoundSql boundSql(String sql, SqlCommandType commandType) {
        Configuration configuration = new Configuration();
        MappedStatement statement = new MappedStatement.Builder(configuration, "test." + commandType.name(),
                new StaticSqlSource(configuration, sql), commandType).build();
        return statement.getBoundSql(null);
    }
}
