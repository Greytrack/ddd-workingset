package com.airedgesoft.lib.restful.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 幂等自动装配（v4-restful-api.md §6.5）：经
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 生效，<b>不依赖</b>宿主的
 * {@code scanBasePackages}（deploy-full 为显式包清单）。
 *
 * <p>
 * <b>幂等防护 = 框架默认行为</b>：{@link IdempotencyFilter} 对所有 {@code POST} 端点自动生效，控制层<b>零注解零代码</b>（2026-10
 * 规范修订，{@code @Idempotent} 注解与 AOP 切面已退役）。
 *
 * <p>
 * <b>实现优先级（v4-restful-api.md §6，2026-10-09 定为 Redis）</b>：
 * <ol>
 * <li><b>Redisson 可用</b>（类路径存在 + 已装配客户端）→ {@link RedissonIdempotencyStore}（由
 * {@link RedissonIdempotencyAutoConfiguration} 装配，deploy-full 经 legacy {@code infrastructure-tools} 已具备）；</li>
 * <li><b>类路径无 Redisson</b>（本地 H2 workshop / 单元测试）→ 本类兜底装配 {@link InMemoryIdempotencyStore}；</li>
 * <li>宿主 / BC 声明自定义 {@link IdempotencyStore} Bean → 覆盖以上两者。</li>
 * </ol>
 */
@AutoConfiguration
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingClass("org.redisson.api.RedissonClient")
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore inMemoryIdempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(IdempotencyFilter.class)
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(IdempotencyStore idempotencyStore,
            ObjectProvider<ObjectMapper> objectMapper) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(
                new IdempotencyFilter(idempotencyStore, objectMapper.getIfAvailable(ObjectMapper::new)));
        registration.addUrlPatterns("/v1/*");
        // 最外层：快照天然包含内层（ETag / Location 等）最终响应头；早于 WebMvc 层一切切面与处理器
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        registration.setName("idempotencyFilter");
        return registration;
    }
}
