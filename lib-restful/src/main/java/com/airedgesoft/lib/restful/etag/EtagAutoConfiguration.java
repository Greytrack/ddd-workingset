package com.airedgesoft.lib.restful.etag;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * ETag 单一意图出口自动装配：注册 {@link EtagResponseAdvice}（BC 可用同型 Bean 覆盖）。 经 {@code AutoConfiguration.imports} 生效，不依赖宿主
 * {@code scanBasePackages}。
 *
 * <p>
 * 控制器返回纯数据读模型（{@code Q*}，无版本字段），advice 恒下发表示 hash {@code ETag}、按需（{@link EnableRepresentationCache}）支持 304，并按端点
 * {@link EtagResource} 身份声明把当前并发代数平铺进 wire（规范 §6.7）。
 */
@AutoConfiguration
public class EtagAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EtagHashCache etagHashCache() {
        return new InMemoryEtagHashCache();
    }

    @Bean
    @ConditionalOnMissingBean
    public EtagResponseAdvice etagResponseAdvice(EtagHashCache etagHashCache) {
        return new EtagResponseAdvice(etagHashCache, null);
    }

    /**
     * 受控 JDBC 写入机制生命周期桥接（宿主引入 lib-jdbc 时激活）：聚合加载 / 保存经全局监听器自动登记并发代数（规范 §6.7）。 监听器为自注册 Bean（初始化挂入 /
     * 销毁注销；注册按实例身份去重，宿主保持单例实例即不累积）。
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "com.airedgesoft.lib.jdbc.JdbcAggregateStore")
    static class JdbcAggregateEtagBridgeConfiguration {

        @Bean
        public EtagAggregateLifecycleListener etagAggregateLifecycleListener() {
            return new EtagAggregateLifecycleListener();
        }
    }

    /**
     * Spring Data JDBC 实体生命周期桥接（宿主引入 spring-data-jdbc 时激活）：聚合加载 / 保存经 {@code JdbcAggregateTemplate} 的
     * {@code EntityCallbacks} 自动登记并发代数（规范 §6.7），仓储与映射器无需手工登记。
     */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "org.springframework.data.jdbc.core.JdbcAggregateTemplate")
    static class JdbcEntityLifecycleConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public EtagEntityLifecycleCallback etagEntityLifecycleCallback(
                org.springframework.data.mapping.context.MappingContext<? extends org.springframework.data.relational.core.mapping.RelationalPersistentEntity<?>, ?> mappingContext) {
            return new EtagEntityLifecycleCallback(mappingContext);
        }

        /**
         * 聚合存库入口（持久化侧）：版本基线由框架取给业务，仓储只管存库。
         */
        @Bean
        @ConditionalOnMissingBean
        public com.airedgesoft.lib.restful.persistence.AggregatePersistence aggregatePersistence(
                org.springframework.data.jdbc.core.JdbcAggregateTemplate template,
                org.springframework.data.relational.core.mapping.RelationalMappingContext mappingContext) {
            return new com.airedgesoft.lib.restful.persistence.AggregatePersistence(template, mappingContext);
        }
    }
}
