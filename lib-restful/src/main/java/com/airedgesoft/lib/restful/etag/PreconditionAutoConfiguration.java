package com.airedgesoft.lib.restful.etag;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 写请求并发版本处理自动装配（决策 C5/C6/D13）：注册 {@link PreconditionAspect}——body 实现 {@link LockedCommand} 即强制携带
 * {@code lockedVersion}（缺失 / 非法 → 400；不一致 → 409）；删除族与 Create 豁免。 {@code airedge.restful.precondition.enabled} 可整体停用。BC
 * 可用同型 Bean 覆盖。
 */
@AutoConfiguration
@EnableConfigurationProperties(PreconditionProperties.class)
public class PreconditionAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
    @ConditionalOnProperty(prefix = "airedge.restful.precondition", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(PreconditionAspect.class)
    public PreconditionAspect preconditionAspect(PreconditionProperties properties) {
        return new PreconditionAspect(properties);
    }
}
