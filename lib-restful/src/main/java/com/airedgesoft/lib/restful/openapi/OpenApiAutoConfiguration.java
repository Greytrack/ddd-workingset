package com.airedgesoft.lib.restful.openapi;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * springdoc 集成自动装配：springdoc 在 classpath 时注册 {@link IdempotencyHeaderOperationCustomizer} （为所有 {@code POST} 端点自动注入
 * {@code Idempotency-Key} 头参数文档——幂等防护为框架默认行为，§6.5）、{@link ReadMaskParameterOperationCustomizer} （为 GET 端点自动注入
 * {@code readMask} 投影参数文档——字段投影为框架全局行为）与 {@link EtagSchemaCustomizer}。springdoc 缺席则整体跳过。
 */
@AutoConfiguration
@ConditionalOnClass(OperationCustomizer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyHeaderOperationCustomizer.class)
    public IdempotencyHeaderOperationCustomizer idempotencyHeaderOperationCustomizer() {
        return new IdempotencyHeaderOperationCustomizer();
    }

    /** {@code Etagged<T>} 平铺展开（lockedVersion 显式进入 schema，与真实 wire 形态一致）。 */
    @Bean
    @ConditionalOnMissingBean(EtagSchemaCustomizer.class)
    public EtagSchemaCustomizer etagSchemaCustomizer() {
        return new EtagSchemaCustomizer();
    }

    /**
     * 裸 {@code Q*} 返回形态的响应组件补 {@code lockedVersion}（C5：lockedVersion 显式进 schema；见
     * {@link LockedVersionSchemaCustomizer}）。
     */
    @Bean
    @ConditionalOnMissingBean(LockedVersionSchemaCustomizer.class)
    public LockedVersionSchemaCustomizer lockedVersionSchemaCustomizer() {
        return new LockedVersionSchemaCustomizer();
    }

    /** GET 端点自动注入 {@code readMask} 投影参数文档（可选字段清单从返回类型派生，与运行时校验同源）。 */
    @Bean
    @ConditionalOnMissingBean(ReadMaskParameterOperationCustomizer.class)
    public ReadMaskParameterOperationCustomizer readMaskParameterOperationCustomizer() {
        return new ReadMaskParameterOperationCustomizer();
    }

    /** 契约参数统一补齐：updateMask / filter / orderBy / validateOnly / pageSize / pageToken / showDeleted（从注解与配置推导）。 */
    @Bean
    @ConditionalOnMissingBean(ContractParameterOperationCustomizer.class)
    public ContractParameterOperationCustomizer contractParameterOperationCustomizer(
            org.springframework.beans.factory.ObjectProvider<com.airedgesoft.lib.restful.pagination.PaginationProperties> pagination,
            org.springframework.beans.factory.ObjectProvider<com.airedgesoft.lib.restful.softdelete.SoftDeleteProperties> softDelete) {
        return new ContractParameterOperationCustomizer(pagination.getIfAvailable(), softDelete.getIfAvailable());
    }
}
