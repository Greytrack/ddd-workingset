package com.airedgesoft.lib.restful.problem;

import com.airedgesoft.lib.exception.DomainException;
import org.springframework.beans.factory.config.MethodInvokingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * RFC 9457 Problem Details 提示词国际化解析装配（v4-restful-api.md §8）。
 *
 * <p>
 * 缺省提供身份解析（直接使用 {@code message}）；宿主 / BC 声明同型 {@link DomainMessageResolver} Bean 即自动接管 {@code detail} 文案解析（经
 * {@link ProblemDetails#setMessageResolver} 注册到静态缝隙）。映射 / 构建逻辑始终委托 {@link ProblemDetails}，各 BC
 * {@code @RestControllerAdvice} 零改动。
 */
@AutoConfiguration
public class ProblemDetailsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DomainMessageResolver.class)
    public DomainMessageResolver defaultDomainMessageResolver() {
        return DomainException::getMessage;
    }

    /** 把已解析出的解析器注入 {@link ProblemDetails} 静态缝隙（先于上面的默认 Bean 声明，保证该类型 Bean 已存在）。 */
    @Bean
    @ConditionalOnBean(DomainMessageResolver.class)
    public static MethodInvokingBean problemDetailsResolverWiring(DomainMessageResolver resolver) {
        MethodInvokingBean wiring = new MethodInvokingBean();
        wiring.setTargetClass(ProblemDetails.class);
        wiring.setTargetMethod("setMessageResolver");
        wiring.setArguments(resolver);
        return wiring;
    }
}
