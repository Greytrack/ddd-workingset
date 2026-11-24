package com.airedgesoft.lib.restful.dryrun;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 预检回滚自动装配（Tier A「validateOnly 预检回滚」）：注册 {@link DryRunAspect}（{@code @DryRun}；事务边界由切面自持， 通过
 * {@link PlatformTransactionManager} 开启 / 加入事务；无事务管理器时降级为直接执行并告警，见组件 javadoc）。
 */
@AutoConfiguration
public class DryRunAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(DryRunAspect.class)
    public DryRunAspect dryRunAspect(ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        return new DryRunAspect(transactionManagerProvider.getIfAvailable());
    }
}
