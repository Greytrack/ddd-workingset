package com.airedgesoft.lib.restful.orderby;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 排序白名单自动装配：注册 {@link OrderByResolver}（{@code @Sortable} 声明式白名单；解析工具 {@link OrderBy#resolve} 为静态工具）。
 */
@AutoConfiguration
public class OrderByAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(OrderByResolver.class)
    public OrderByResolver orderByResolver() {
        return new OrderByResolver();
    }
}
