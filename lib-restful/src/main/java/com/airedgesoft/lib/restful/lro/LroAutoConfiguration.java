package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.restful.pagination.PageSizeResolver;
import com.airedgesoft.lib.restful.pagination.PageTokens;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * LRO 自动装配（AIP-151；经 {@code AutoConfiguration.imports} 生效）。
 *
 * <p>
 * <b>装配内容</b>：① 无 Redisson 类路径时兜底 {@link InMemoryOperationStore}（Redis 优先装配见 {@link LroRedissonAutoConfiguration}； BC
 * 声明同型 Bean 覆盖两者）；② Web 应用 + 开关开启时注册 {@link LroOperationAspect}（202 + Location + Retry-After 装配）与
 * {@link OperationsController}（全局 {@code /v1/operations} 轮询 / 列表（游标分页，复用
 * {@link com.airedgesoft.lib.restful.pagination.PaginationAutoConfiguration} 装配的 {@link PageTokens} /
 * {@link PageSizeResolver}）/ 取消 / 删除端点）及 {@link LroProblemAdvice}（lib 自托管端点的 RFC 9457 错误契约兜底）。
 */
@AutoConfiguration
@EnableConfigurationProperties(LroProperties.class)
public class LroAutoConfiguration {

    @Bean
    @ConditionalOnMissingClass("org.redisson.api.RedissonClient")
    @ConditionalOnMissingBean(OperationStore.class)
    public OperationStore inMemoryOperationStore() {
        return new InMemoryOperationStore();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
    @ConditionalOnProperty(prefix = "airedge.restful.lro", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(LroOperationAspect.class)
    public LroOperationAspect lroOperationAspect(LroProperties properties,
            org.springframework.beans.factory.ObjectProvider<OperationOwnerResolver> ownerResolver,
            OperationStore operationStore) {
        return new LroOperationAspect(properties, ownerResolver.getIfAvailable(), operationStore);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "airedge.restful.lro", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(OperationsController.class)
    public OperationsController operationsController(OperationStore store,
            org.springframework.beans.factory.ObjectProvider<OperationOwnerResolver> ownerResolver,
            PageTokens pageTokens, PageSizeResolver pageSizeResolver) {
        return new OperationsController(store, ownerResolver.getIfAvailable(), pageTokens, pageSizeResolver);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
    @ConditionalOnProperty(prefix = "airedge.restful.lro", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(LroProblemAdvice.class)
    public LroProblemAdvice lroProblemAdvice() {
        return new LroProblemAdvice();
    }
}
