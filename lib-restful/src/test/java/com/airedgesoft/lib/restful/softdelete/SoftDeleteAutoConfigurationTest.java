package com.airedgesoft.lib.restful.softdelete;

import com.airedgesoft.lib.restful.softdelete.mybatis.SoftDeleteInnerInterceptor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 软删除自动装配契约：enabled=true 必须声明 tables 白名单（共享拦截器链会波及无软删列的 legacy 表）； 列名装配期校验；auto-register 缺省自动挂链首（BC 可关闭自管）；BC 同型 Bean
 * 覆盖时装配退让。
 */
class SoftDeleteAutoConfigurationTest {

    @Test
    void disabled_registersNoInterceptor() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of())) {
            ctx.refresh();
            assertThat(ctx.getBeanNamesForType(SoftDeleteInnerInterceptor.class)).isEmpty();
        }
    }

    @Test
    void enabledWithTables_registersInterceptorAndConfiguresContext() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of("airedge.restful.soft-delete.enabled", "true",
                "airedge.restful.soft-delete.tables[0]", "demo_task_write"))) {
            ctx.refresh();
            assertThat(ctx.getBean(SoftDeleteInnerInterceptor.class)).isNotNull();
            assertThat(SoftDeleteContext.tables()).containsExactly("demo_task_write");
            assertThat(SoftDeleteContext.flagColumn()).isEqualTo("delete_flag");
            assertThat(SoftDeleteContext.timeColumn()).isEqualTo("updated_time");
        }
    }

    @Test
    void enabledWithoutTables_failsFast() {
        AnnotationConfigApplicationContext ctx = context(Map.of("airedge.restful.soft-delete.enabled", "true"));
        assertThatThrownBy(ctx::refresh).hasStackTraceContaining("tables 白名单");
        ctx.close();
    }

    @Test
    void bcInterceptorBean_autoConfigurationBacksOff() {
        AnnotationConfigApplicationContext ctx = context(Map.of("airedge.restful.soft-delete.enabled", "true",
                "airedge.restful.soft-delete.tables[0]", "demo_task_write"), BcInterceptorConfig.class);
        ctx.refresh();
        try (ctx) {
            assertThat(ctx.getBeanNamesForType(SoftDeleteInnerInterceptor.class))
                    .containsExactly("bcSoftDeleteInnerInterceptor");
        }
    }

    @Test
    void invalidFlagColumn_failsFastAtAssembly() {
        AnnotationConfigApplicationContext ctx = context(
                Map.of("airedge.restful.soft-delete.enabled", "true", "airedge.restful.soft-delete.tables[0]",
                        "demo_task_write", "airedge.restful.soft-delete.flag-column", "bad;drop table"));
        assertThatThrownBy(ctx::refresh).hasStackTraceContaining("flag-column 非法");
        ctx.close();
    }

    @Test
    void autoRegister_attachesInterceptorToChainHead() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of("airedge.restful.soft-delete.enabled", "true",
                "airedge.restful.soft-delete.tables[0]", "demo_task_write"), HostChainConfig.class)) {
            ctx.refresh();
            // 宿主已声明链 Bean：兜底链退位，注册器挂宿主链首
            assertThat(ctx.getBeanNamesForType(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class))
                    .containsExactly("mybatisPlusInterceptor");
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor chain = ctx
                    .getBean(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class);
            assertThat(chain.getInterceptors()).isNotEmpty();
            assertThat(chain.getInterceptors().get(0)).isInstanceOf(SoftDeleteInnerInterceptor.class);
        }
    }

    @Test
    void enabledWithoutHostChain_createsFallbackChain() {
        // 宿主未声明 MybatisPlusInterceptor Bean：自建兜底链（否则注册器无处可挂，拦截器形同虚设）
        try (AnnotationConfigApplicationContext ctx = context(Map.of("airedge.restful.soft-delete.enabled", "true",
                "airedge.restful.soft-delete.tables[0]", "demo_task_write"))) {
            ctx.refresh();
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor chain = ctx
                    .getBean(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class);
            assertThat(chain.getInterceptors()).hasSize(1);
            assertThat(chain.getInterceptors().get(0)).isInstanceOf(SoftDeleteInnerInterceptor.class);
        }
    }

    @Test
    void disabled_registersNoFallbackChain() {
        try (AnnotationConfigApplicationContext ctx = context(Map.of())) {
            ctx.refresh();
            assertThat(ctx.getBeanNamesForType(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class))
                    .isEmpty();
        }
    }

    @Test
    void autoRegisterDisabled_leavesHostChainUntouched() {
        try (AnnotationConfigApplicationContext ctx = context(
                Map.of("airedge.restful.soft-delete.enabled", "true", "airedge.restful.soft-delete.tables[0]",
                        "demo_task_write", "airedge.restful.soft-delete.auto-register", "false"),
                HostChainConfig.class)) {
            ctx.refresh();
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor chain = ctx
                    .getBean(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class);
            assertThat(chain.getInterceptors()).noneMatch(i -> i instanceof SoftDeleteInnerInterceptor);
        }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties,
            Class<?>... bcConfigurations) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("soft-delete-test", properties));
        // 模拟 Boot 的自动装配后置顺序：BC 配置先注册，@ConditionalOnMissingBean 才看得到 BC 的拦截器 Bean
        if (bcConfigurations.length > 0) {
            ctx.register(bcConfigurations);
        }
        ctx.register(SoftDeleteAutoConfiguration.class);
        return ctx;
    }

    @Configuration(proxyBeanMethods = false)
    static class BcInterceptorConfig {

        @Bean
        SoftDeleteInnerInterceptor bcSoftDeleteInnerInterceptor() {
            return new SoftDeleteInnerInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostChainConfig {

        @Bean
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor mybatisPlusInterceptor() {
            return new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        }
    }
}
