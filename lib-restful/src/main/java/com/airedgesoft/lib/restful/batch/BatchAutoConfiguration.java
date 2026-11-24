package com.airedgesoft.lib.restful.batch;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 批量契约自动装配：注册 {@link BatchProperties}（全局上限 {@code airedge.restful.batch.max}，缺省 200）与
 * {@link BatchLimitAspect}（{@code @BatchLimit} 条数上限；对齐工具 {@link BatchAlignment} 为静态工具）。
 */
@AutoConfiguration
@EnableConfigurationProperties(BatchProperties.class)
public class BatchAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(BatchLimitAspect.class)
    public BatchLimitAspect batchLimitAspect(BatchProperties properties) {
        return new BatchLimitAspect(properties.getMax());
    }
}
