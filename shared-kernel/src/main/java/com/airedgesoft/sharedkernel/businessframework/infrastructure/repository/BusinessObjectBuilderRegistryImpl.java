package com.airedgesoft.sharedkernel.businessframework.infrastructure.repository;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder.BusinessObjectBuilder;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder.BusinessObjectBuilderRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务对象构造器注册中心实现：分类 type → 构造器 的内存索引（纯 Java，无框架依赖；装配在 deploy-full）。
 *
 * <p>
 * 注册 / 冲突口径镜像旧空间 {@code DataObjectRegistry.register}（backend infrastructure/sdkImpl service/common）： 无效入参告警跳过；同 type
 * <b>同构造器类</b>重复注册（含同类不同实例）静默保留首个；同 type <b>异类</b>构造器冲突告警后覆盖（后到者生效）。 未命中<b>不返回 null</b>——按契约抛 {@link DomainException}。
 *
 * <p>
 * ⚠️ 「子分类未注册构造器时沿类继承链回退到基类构造器」需要分类元数据（type → 子分类 class 映射）， v4 分类元数据落地前本实现只做精确匹配——与装配侧显式列全分类节点的口径配套， 路由结果等价。
 *
 * <p>
 * <b>回退接缝</b>：本实现只提供精确查找；回退链（父节点解析）与回退编排在 bridge-v3 的装饰器中完成（2026-11-16 用户拍板）， 注册中心模型本期不改。
 */
@Slf4j
public class BusinessObjectBuilderRegistryImpl implements BusinessObjectBuilderRegistry {

    private final Map<Long, BusinessObjectBuilder<?>> builders = new ConcurrentHashMap<>();

    @Override
    public BusinessObjectBuilder<?> getBuilder(Long type) {
        BusinessObjectBuilder<?> builder = type == null ? null : builders.get(type);
        if (builder == null) {
            throw new DomainException(SharedKernelResultCode.BUILDER_NOT_REGISTERED, type);
        }
        return builder;
    }

    @Override
    public Optional<BusinessObjectBuilder<?>> findBuilder(Long type) {
        return type == null ? Optional.empty() : Optional.ofNullable(builders.get(type));
    }

    @Override
    public void register(Long type, BusinessObjectBuilder<?> builder) {
        if (type == null || builder == null) {
            log.warn("尝试注册无效的业务对象构造器: type={}, builder={}", type, builder);
            return;
        }
        BusinessObjectBuilder<?> existing = builders.get(type);
        if (existing != null) {
            if (existing.getClass().equals(builder.getClass())) {
                return;
            }
            log.warn("分类 type {} 的构造器已被 {} 注册，将被 {} 覆盖", type, existing.getClass().getName(),
                    builder.getClass().getName());
        }
        builders.put(type, builder);
        log.debug("注册业务对象构造器: type={}, builder={}", type, builder.getClass().getSimpleName());
    }
}
