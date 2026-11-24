package com.airedgesoft.lib.restful.pagination;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 集合查询分页装配（v4-restful-api.md §7）：注册 {@link PaginationProperties}，装配 AES-GCM 加密游标 {@link PageTokens} 与默认条数来源
 * {@link PageSizeDefaultSource}。各 BC QueryService / Controller 注入这些 bean 取 pageSize 默认/上限与游标编解码。
 *
 * <p>
 * <b>默认条数优先级</b>：宿主注入的 {@link PageSizeDefaultSource}（如 bridge-v3 经 v3 智能规则「页面滚动加载数量」）＞ 本类缺省实现 （回落
 * {@link PaginationProperties#getDefaultPageSize()}）。BC / 宿主声明同型 Bean 即覆盖。
 */
@AutoConfiguration
@EnableConfigurationProperties(PaginationProperties.class)
public class PaginationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PageTokens.class)
    public PageTokens pageTokens(PaginationProperties properties) {
        return new PageTokens(properties.getPageTokenSecret(), properties.getPageTokenTtl());
    }

    /** 缺省默认条数来源：回落配置值；被宿主实现（v3 智能规则桥接）覆盖。 */
    @Bean
    @ConditionalOnMissingBean(PageSizeDefaultSource.class)
    public PageSizeDefaultSource pageSizeDefaultSource(PaginationProperties properties) {
        return properties::getDefaultPageSize;
    }

    /** pageSize 语义唯一落点（缺省 / 400 / 上限钳制），供各 BC QueryService / Controller 注入使用。 */
    @Bean
    @ConditionalOnMissingBean(PageSizeResolver.class)
    public PageSizeResolver pageSizeResolver(PaginationProperties properties, PageSizeDefaultSource defaultSource) {
        return new PageSizeResolver(properties, defaultSource);
    }
}
