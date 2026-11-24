package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder;

import java.util.Optional;

/**
 * 业务对象构造器注册中心。
 *
 * <p>
 * 对应 14 需求建模 / 08 多版次能力 新类图 {@code <<Registry 注册中心构造器>>}（2026-09-23 定稿：工厂 → 构造器 Builder 模式）：
 *
 * <pre>
 * class BusinessObjectBuilderRegistry {
 *     + getBuilder(type) BusinessObjectBuilder
 *     + register(type, builder) void
 * }
 * </pre>
 *
 * <p>
 * 用途：应用层按创建入参的 {@code type}（分类标识）取到对应的具体构造器，从而在不感知具体聚合类型的前提下走同一套创建编排
 * （{@link BusinessObjectBuilder#build(CreateInput)}）；新增一个子分类 = 注册一个构造器，不改既有代码。
 *
 * <p>
 * <b>边界</b>：本接口是**领域端口**（纯契约、零框架依赖），实现在基础设施层（可用 Spring 容器装配）。 返回类型用 {@link BusinessObjectBuilder}{@code <?>}
 * 而非泛型参数，因为注册中心按运行期 {@code type} 路由， 调用方拿到的构造器类型本就只能在运行期确定。
 *
 * <p>
 * <b>继承链回退</b>：类图口径为「沿类继承链查找——未命中取父类 class 向上，直到基类默认构造器」； 该机制需要分类元数据（type → 子分类 class 映射）， v4
 * 分类元数据落地前实现只做精确匹配（子分类经装配侧显式列全节点，路由结果等价）。
 *
 * <p>
 * <b>回退接缝（2026-11-16 用户拍板）</b>：回退链解析与回退编排由 bridge-v3 承担（{@code V3TypeHierarchySource} + 回退装饰器），v4 侧只提供「精确查找、未命中不抛错」的
 * {@link #findBuilder(Long)} 供桥接层循环使用—— 本期不重构注册中心模型，也不新增父链端口。
 */
public interface BusinessObjectBuilderRegistry {

    /**
     * 按类型取构造器。
     *
     * @param type
     *            分类标识
     * @return 对应构造器，绝不为 null
     */
    BusinessObjectBuilder<?> getBuilder(Long type);

    /**
     * 按类型精确查找构造器（未命中返回空，不抛错）。
     *
     * <p>
     * 供 bridge-v3 的回退装饰器循环使用：装饰器先用本方法查当前 type，未命中再沿分类父链上溯， 直到命中已注册构造器或到顶（到顶后仍由 {@link #getBuilder(Long)} 的原错误语义兜底）。
     *
     * @param type
     *            分类标识（可空）
     * @return 已注册的构造器；未注册或 type 为空返回 {@link Optional#empty()}
     */
    Optional<BusinessObjectBuilder<?>> findBuilder(Long type);

    /** 注册构造器。 */
    void register(Long type, BusinessObjectBuilder<?> builder);
}
