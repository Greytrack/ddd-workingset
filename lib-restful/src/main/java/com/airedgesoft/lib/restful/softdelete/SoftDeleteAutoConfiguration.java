package com.airedgesoft.lib.restful.softdelete;

import com.airedgesoft.lib.restful.softdelete.mybatis.SoftDeleteChainRegistrar;
import com.airedgesoft.lib.restful.softdelete.mybatis.SoftDeleteInnerInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 软删除自动装配（v4-restful-api.md §5.1）：经 {@code AutoConfiguration.imports} 生效，不依赖宿主 {@code scanBasePackages}。
 *
 * <p>
 * <b>装配内容</b>：注册 {@link SoftDeleteProperties}（{@code airedge.restful.soft-delete.*}）并把配置挂入 {@link SoftDeleteContext}；
 * 类路径存在 MyBatis-Plus 且 {@code enabled=true} 时注册读模型侧 {@link SoftDeleteInnerInterceptor} Bean，并经
 * {@link SoftDeleteChainRegistrar} <b>自动挂进宿主 {@code MybatisPlusInterceptor} 链首</b>（{@code auto-register} 缺省 true； BC
 * 自管次序时置 false 自行 {@code addInnerInterceptor(...)}）。宿主未声明链 Bean 时，本装配<b>自建兜底链</b> （{@code @ConditionalOnMissingBean}
 * 退位式：宿主后补链则兜底链退位、注册器改挂宿主链首），保证拦截器开箱即生效。 {@code enabled=true} 时 {@code tables} 白名单必填（缺失即启动失败）：拦截器对共享链上全部 Mapper 生效，
 * 未限定表会改写无软删列的表（如 legacy 表）。
 *
 * <p>
 * 写路径不经本装配（无任何写语句自动织入）：软删 / 恢复 / 彻底删除由应用层 → 仓储显式语句执行（lib-jdbc {@code JdbcSoftDelete} /
 * {@code AggregateSoftDelete}，列模型声明从 {@link SoftDeleteContext} 取值构造）；列模型沿用 v3 既有表 （{@code delete_flag} +
 * {@code updated_time}），<b>不需要也不允许为软删另加列</b>。
 */
@AutoConfiguration
@EnableConfigurationProperties(SoftDeleteProperties.class)
public class SoftDeleteAutoConfiguration {

    public SoftDeleteAutoConfiguration(SoftDeleteProperties properties) {
        SoftDeleteContext.configure(properties);
    }

    @Bean
    @ConditionalOnClass(com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor.class)
    @ConditionalOnProperty(prefix = "airedge.restful.soft-delete", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(SoftDeleteInnerInterceptor.class)
    public SoftDeleteInnerInterceptor softDeleteInnerInterceptor(SoftDeleteProperties properties) {
        if (properties.getTables() == null || properties.getTables().isEmpty()) {
            throw new IllegalStateException("airedge.restful.soft-delete.enabled=true 必须声明 tables 白名单"
                    + "（具备 delete_flag + updated_time 列的表）：拦截器挂在共享 MybatisPlusInterceptor 链上会对全部 Mapper 生效，"
                    + "未限定表会改写无软删列的表（如 legacy 表）");
        }
        return new SoftDeleteInnerInterceptor();
    }

    /** 自动挂链注册器（链首，先于分页；幂等追加，不替换宿主链）。BC 自管次序时 {@code auto-register=false} 关闭。 */
    @Bean
    @ConditionalOnClass(com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor.class)
    @ConditionalOnProperty(prefix = "airedge.restful.soft-delete", name = "auto-register", havingValue = "true", matchIfMissing = true)
    static BeanPostProcessor softDeleteChainRegistrar(ObjectProvider<SoftDeleteInnerInterceptor> interceptor) {
        return new SoftDeleteChainRegistrar(interceptor);
    }

    /**
     * 宿主未声明 {@code MybatisPlusInterceptor} 链 Bean 时的兜底链（仅含软删拦截器）：无链则 {@link SoftDeleteChainRegistrar}
     * 无处可挂、拦截器形同虚设。宿主声明链 Bean 后本 Bean 自动退位，注册器改挂宿主链首。
     */
    @Bean
    @ConditionalOnClass(com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor.class)
    @ConditionalOnProperty(prefix = "airedge.restful.soft-delete", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class)
    public com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor softDeleteFallbackChain(
            ObjectProvider<SoftDeleteInnerInterceptor> interceptor) {
        com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor chain = new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        interceptor.ifAvailable(chain::addInnerInterceptor);
        return chain;
    }
}
