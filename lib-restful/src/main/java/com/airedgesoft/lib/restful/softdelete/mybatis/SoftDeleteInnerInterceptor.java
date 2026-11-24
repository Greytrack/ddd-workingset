package com.airedgesoft.lib.restful.softdelete.mybatis;

import com.airedgesoft.lib.restful.softdelete.SoftDeleteContext;
import com.airedgesoft.lib.restful.softdelete.SoftDeleteSqlRewriter;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * MyBatis-Plus 软删除拦截器（<b>读模型侧</b>；v4-restful-api.md §5.1，Tier A「读路径框架统一处理」落点）。
 *
 * <p>
 * <b>列模型沿用 v3 既有表</b>：{@code delete_flag}（int4，0 存活 / 1 已删）+ {@code updated_time}（int8 毫秒，已删行的该值即删除时刻）。 SELECT 注入
 * {@code delete_flag = 0}（改写逻辑收口 {@link SoftDeleteSqlRewriter}，本类只做 MyBatis 织入壳）。 写语句（DELETE / UPDATE / INSERT）一律不改写：软删
 * / 恢复 / 彻底删除是领域生命周期决策，由应用层 → 仓储显式语句执行 （lib-jdbc {@code JdbcSoftDelete} /
 * {@code AggregateSoftDelete}），删除语义由仓储语句明说，不经框架静默改写。
 *
 * <p>
 * <b>为什么不用内置 {@code @TableLogic}</b>：内置逻辑删除按实体注解静态判定、不支持 {@code ?showDeleted=true} 请求级放行，
 * 也不会在软删时刷新时间列（回收站需要删除时刻），更无表级作用域——故以 {@link InnerInterceptor} 自定义（仅读侧过滤）。
 *
 * <p>
 * <b>接入</b>：{@code SoftDeleteAutoConfiguration} 按类路径（MyBatis-Plus 存在）+ {@code airedge.restful.soft-delete.enabled=true}
 * 注册本拦截器 Bean，并经 {@link SoftDeleteChainRegistrar} 自动追加到宿主 {@code MybatisPlusInterceptor} <b>链首</b>
 * （{@code auto-register} 缺省 true；先于分页，避免 count 统计已删行；不另声明同型 Bean，以免 legacy 链退让）。 BC 自管次序时置 {@code auto-register=false}
 * 自行挂链。
 *
 * <p>
 * <b>作用域</b>：{@code airedge.restful.soft-delete.tables} 白名单限定主表（{@code enabled=true} 时必填）——共享链上还有 legacy
 * 表与无软删两列的表，未限定会改写它们的语句；白名单外表原样放行。
 */
public class SoftDeleteInnerInterceptor implements InnerInterceptor {

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
            org.apache.ibatis.session.RowBounds rowBounds, org.apache.ibatis.session.ResultHandler resultHandler,
            BoundSql boundSql) {
        String rewritten = SoftDeleteSqlRewriter.rewrite(boundSql.getSql(), SoftDeleteContext.deletedIncluded());
        if (rewritten != null && !rewritten.equals(boundSql.getSql())) {
            PluginUtils.mpBoundSql(boundSql).sql(rewritten);
        }
    }
}
