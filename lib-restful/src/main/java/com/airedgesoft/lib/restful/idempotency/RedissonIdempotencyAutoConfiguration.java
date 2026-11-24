package com.airedgesoft.lib.restful.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 幂等键存储的 <b>Redis 装配（默认实现，v4-restful-api.md §6）</b>：类路径存在 Redisson 时生效——已装配 {@link RedissonClient} 则注册
 * {@link RedissonIdempotencyStore}（Redis；deploy-full 经 legacy {@code infrastructure-tools} 已具备）；仅有 Jar 而无客户端（未配置
 * Redis）则回退 {@link InMemoryIdempotencyStore}。
 *
 * <p>
 * <b>类级 {@code @ConditionalOnClass}</b>：Redisson 不在类路径（本地 H2 workshop、无 Redis 的宿主）时整类被跳过——避免方法签名中的
 * {@code RedissonClient} 在类加载 / 方法内省期触发 {@code NoClassDefFoundError}；此时由 {@link IdempotencyAutoConfiguration} 兜底。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
public class RedissonIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore redisIdempotencyStore(ObjectProvider<RedissonClient> client,
            ObjectProvider<ObjectMapper> objectMapper) {
        RedissonClient redisson = client.getIfAvailable();
        if (redisson == null) {
            return new InMemoryIdempotencyStore();
        }
        return new RedissonIdempotencyStore(redisson, objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
