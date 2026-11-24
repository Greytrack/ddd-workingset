package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.restful.fieldtoggle.FieldToggleProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * readMask 字段投影自动装配（Tier A 全局拦截）：Servlet Web + {@code airedge.restful.readmask.enabled=true}（缺省 true）时注册全局
 * {@link ReadMaskAspect}（业务零注解零代码；经 {@code AutoConfiguration.imports} 生效，不依赖宿主 {@code scanBasePackages}）。
 * 停用预定义字段（{@code fieldtoggle} SPI）经 {@link ObjectProvider} 惰性并入剪枝通道——无 Provider Bean 时同样静默放行。 BC 可用同型 Bean
 * 覆盖；关闭开关即整组读投影通道停用（仅建议迁移期旧端点使用）。
 */
@AutoConfiguration
public class ReadMaskAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
    @ConditionalOnProperty(prefix = "airedge.restful.readmask", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ReadMaskAspect.class)
    public ReadMaskAspect readMaskAspect(ObjectProvider<FieldToggleProvider> toggleProvider) {
        return new ReadMaskAspect(toggleProvider);
    }
}
