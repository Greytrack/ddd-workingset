package com.airedgesoft.lib.restful.abac;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * ABAC 自动装配（§19；裁决批 2：{@code _access} 伴随方案——ABAC 应用层不影响接口定义）。
 *
 * <p>
 * <b>装配内容</b>：缺省 {@link PermitAllAccessPolicyProvider}（全允许，BC / 宿主声明同型 Bean 即接管裁决）； Web 应用 +
 * {@code airedge.restful.abac.enabled=true}（缺省 true）时注册 {@link IncludeAccessAspect}（脱敏恒生效 + {@code includeAccess}
 * 挂节点）。写路径 {@link AbacWriteGuard} 为静态工具（无 Bean），由 adapters 在 §19.3 五步链第 3 / 5 步显式调用。
 */
@AutoConfiguration
public class AbacAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AccessPolicyProvider.class)
    public AccessPolicyProvider permitAllAccessPolicyProvider() {
        return new PermitAllAccessPolicyProvider();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter")
    @ConditionalOnProperty(prefix = "airedge.restful.abac", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(IncludeAccessAspect.class)
    public IncludeAccessAspect includeAccessAspect(AccessPolicyProvider provider) {
        return new IncludeAccessAspect(provider);
    }
}
