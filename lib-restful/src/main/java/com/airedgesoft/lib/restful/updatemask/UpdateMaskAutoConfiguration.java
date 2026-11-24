package com.airedgesoft.lib.restful.updatemask;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * updateMask 校验自动装配：注册 {@link UpdateMaskAspect}（{@code @ValidUpdateMask} 声明式校验；工具链 {@link UpdateMask} /
 * {@link ModelPatcher} / {@link PropertyAccess} 为静态工具，无需装配）。BC 可用同型 Bean 覆盖。
 */
@AutoConfiguration
public class UpdateMaskAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(UpdateMaskAspect.class)
    public UpdateMaskAspect updateMaskAspect() {
        return new UpdateMaskAspect();
    }
}
