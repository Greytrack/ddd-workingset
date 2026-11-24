package com.airedgesoft.lib.restful.softdelete;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 软删除「本次查询是否放行已删行」的请求级上下文 + 列模型统一出口。
 *
 * <p>
 * <b>判定优先级</b>：编程式覆盖（{@link #withDeletedIncluded(Runnable)} / {@link #deletedIncludedOverride()}，供回收站 Service、批处理作业等非
 * HTTP 线程使用）＞ 当前请求参数（{@code ?showDeleted=true}，参数名可配 {@link SoftDeleteProperties#setShowDeletedParam}）＞ 缺省过滤已删行。
 * 读模型查询改写（{@link com.airedgesoft.lib.restful.softdelete.mybatis.SoftDeleteInnerInterceptor}）统一读本上下文； 写路径软删语句（lib-jdbc
 * {@code JdbcSoftDelete} / {@code AggregateSoftDelete}）的列模型声明亦从本上下文取值构造，各 BC 禁止自拼 {@code delete_flag} 条件。
 *
 * <p>
 * <b>列模型</b>（沿用 v3 既有表）：{@code delete_flag}（0 存活 / 1 已删）+ {@code updated_time}（毫秒，已删行的该值即删除时刻）。
 */
public final class SoftDeleteContext {

    private static final ThreadLocal<Boolean> OVERRIDE = new ThreadLocal<>();

    private static volatile SoftDeleteProperties properties = new SoftDeleteProperties();

    private SoftDeleteContext() {
    }

    /** 注册配置（由 {@code SoftDeleteAutoConfiguration} 在装配期调用；未装配时用缺省配置）。列名非法即抛错，不进入运行期。 */
    static void configure(SoftDeleteProperties configured) {
        if (configured != null) {
            configured.validate();
            properties = configured;
        }
    }

    /** 删除标志列名（DDL / SQL 构建统一取用）。 */
    public static String flagColumn() {
        return properties.getFlagColumn();
    }

    /** 「存活」标志值。 */
    public static int aliveValue() {
        return properties.getAliveValue();
    }

    /** 「已删除」标志值。 */
    public static int deletedValue() {
        return properties.getDeletedValue();
    }

    /** 时间列名（软删 / 恢复同语句刷新；已删行的该值即删除时刻）。 */
    public static String timeColumn() {
        return properties.getTimeColumn();
    }

    /** 表白名单（小写归一；空集 = 未限定，仅工具直调语义，拦截器装配要求 enabled 时必填）。 */
    public static Set<String> tables() {
        return properties.getTables().stream().map(table -> table.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 该表是否在软删作用域内（白名单为空 = 不限定；表名大小写不敏感，忽略 schema 前缀与引号）。 */
    public static boolean inScope(String table) {
        Set<String> scoped = tables();
        if (scoped.isEmpty()) {
            return true;
        }
        if (table == null) {
            return false;
        }
        String normalized = table.toLowerCase(Locale.ROOT).replace("\"", "");
        int dot = normalized.lastIndexOf('.');
        return scoped.contains(dot < 0 ? normalized : normalized.substring(dot + 1));
    }

    /** 本次是否放行已删行（优先级见类 javadoc）。 */
    public static boolean deletedIncluded() {
        Boolean override = OVERRIDE.get();
        if (override != null) {
            return override;
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String raw = request.getParameter(properties.getShowDeletedParam());
        return raw != null && ("true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()));
    }

    /** 编程式放行范围（线程作用域；回收站视图 / 定时作业使用，务必在 finally 还原）。 */
    public static void withDeletedIncluded(Runnable action) {
        OVERRIDE.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            OVERRIDE.remove();
        }
    }

    /** 当前线程编程式覆盖值（未覆盖返回 null）。 */
    public static Boolean deletedIncludedOverride() {
        return OVERRIDE.get();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
