package com.airedgesoft.lib.restful.fieldtoggle;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 预定义字段开关自动装配（Tier B 收口执行）：宿主存在 {@link FieldToggleProvider} Bean（BC 声明停用注册表）时注册写路径 {@link FieldToggleWriteAspect}；<b>无
 * Provider Bean 时本装配整体不激活</b>——读写路径零开销（{@code @ConditionalOnBean} 静默跳过）。 读路径剔除并入 {@code readmask.ReadMaskAspect}（其经
 * {@code ObjectProvider} 惰性取用 Provider，无 Bean 同样静默放行）。BC 可用同型 Bean 覆盖切面。
 */
@AutoConfiguration
public class FieldToggleAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(FieldToggleProvider.class)
    @ConditionalOnMissingBean(FieldToggleWriteAspect.class)
    public FieldToggleWriteAspect fieldToggleWriteAspect(FieldToggleProvider provider) {
        return new FieldToggleWriteAspect(provider);
    }
}
