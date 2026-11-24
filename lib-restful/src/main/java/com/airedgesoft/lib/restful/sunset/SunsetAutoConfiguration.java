package com.airedgesoft.lib.restful.sunset;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sunset / Deprecation 响应头自动装配（RFC 8594）：Servlet Web 应用时注册全局 {@link SunsetInterceptor} （对所有 Handler 生效，命中
 * {@link Sunset} 注解才下发头——头值经 {@code Instant.parse} 解析，注解常量非法在首次命中即暴露）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SunsetAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SunsetInterceptor.class)
    public SunsetInterceptor sunsetInterceptor() {
        return new SunsetInterceptor();
    }

    @Bean
    public WebMvcConfigurer sunsetWebMvcConfigurer(SunsetInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }
}
