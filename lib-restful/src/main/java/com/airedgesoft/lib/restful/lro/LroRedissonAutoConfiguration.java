package com.airedgesoft.lib.restful.lro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * LRO 状态存储的 <b>Redis 装配</b>（多实例共享轮询状态）：类路径存在 Redisson 时生效——已装配 {@link RedissonClient} 则注册
 * {@link RedissonOperationStore}；仅有 Jar 而无客户端（未配置 Redis）则回退 {@link InMemoryOperationStore}，由
 * {@link LroAutoConfiguration} 兜底装配。
 *
 * <p>
 * <b>类级 {@code @ConditionalOnClass}</b>（name 形态）：Redisson 不在类路径（本地 H2 workshop）时整类被跳过——避免方法签名中的 {@code RedissonClient}
 * 触发 {@code NoClassDefFoundError}（与幂等组件同型）。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
@EnableConfigurationProperties(LroProperties.class)
public class LroRedissonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OperationStore.class)
    public OperationStore redisOperationStore(ObjectProvider<RedissonClient> client,
            ObjectProvider<ObjectMapper> objectMapper, LroProperties properties) {
        RedissonClient redisson = client.getIfAvailable();
        if (redisson == null) {
            return new InMemoryOperationStore();
        }
        return new RedissonOperationStore(redisson, objectMapper.getIfAvailable(ObjectMapper::new),
                properties.getOperationTtl());
    }
}
