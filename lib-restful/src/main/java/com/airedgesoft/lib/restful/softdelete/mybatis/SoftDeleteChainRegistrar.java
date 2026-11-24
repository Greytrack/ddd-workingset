package com.airedgesoft.lib.restful.softdelete.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 软删拦截器<b>自动挂链</b>（Tier A 读路径「框架完全接管」的最后一环）：把容器中的 {@link SoftDeleteInnerInterceptor} 追加到宿主
 * {@link MybatisPlusInterceptor} 执行链<b>链首</b>。
 *
 * <p>
 * <b>为什么是链首</b>：分页拦截器会据当前 SQL 生成 count 语句——软删注入若在其后，count 将统计已删行。
 *
 * <p>
 * <b>为什么用 BeanPostProcessor 而非另声明链 Bean</b>：宿主（如 legacy {@code MybatisPlusConfiguration}）的链常带
 * {@code @ConditionalOnMissingBean(MybatisPlusInterceptor.class)}，另声明同型 Bean 会让宿主链整体退让、丢掉分页与乐观锁拦截器。
 * 本注册器只<b>追加</b>，不替换；链上已有软删拦截器时幂等跳过。
 *
 * <p>
 * 由 {@code SoftDeleteAutoConfiguration} 在 {@code airedge.restful.soft-delete.auto-register}（缺省 true）时注册； BC 需要自管次序时置
 * false 并自行挂链。
 */
public class SoftDeleteChainRegistrar implements BeanPostProcessor {

    private final ObjectProvider<SoftDeleteInnerInterceptor> interceptor;

    public SoftDeleteChainRegistrar(ObjectProvider<SoftDeleteInnerInterceptor> interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof MybatisPlusInterceptor chain) {
            interceptor.ifAvailable(softDelete -> addToChainHead(chain, softDelete));
        }
        return bean;
    }

    private static void addToChainHead(MybatisPlusInterceptor chain, SoftDeleteInnerInterceptor softDelete) {
        List<InnerInterceptor> inner = new ArrayList<>(chain.getInterceptors());
        if (inner.stream().anyMatch(SoftDeleteInnerInterceptor.class::isInstance)) {
            return;
        }
        inner.add(0, softDelete);
        chain.setInterceptors(inner);
    }
}
