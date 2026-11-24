package com.airedgesoft.lib.restful.filter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * AIP-160 过滤契约自动装配：注册 {@link FilterAspect}（{@code @Filterable} 声明式白名单；解析 / 校验 / 下推工具链 {@link FilterParser} /
 * {@link FilterValidator} / {@link FilterAst} + {@link FilterTranslator} 为静态与 SPI 工具，无需装配）。
 */
@AutoConfiguration
public class FilterAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(FilterAspect.class)
    public FilterAspect filterAspect() {
        return new FilterAspect();
    }
}
