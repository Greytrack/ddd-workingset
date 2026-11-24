package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification;

/**
 * 分类层级事实源（过渡期契约）：分类 type → 父节点 type。
 *
 * <p>
 * <b>为何需要</b>：v3 共表协议要求实例落库时写入父类 id（{@code air_inst_common.class}），构造器继承回退也需要父链。 这些是分类元数据能力（v3 {@code AdminCache}
 * 元数据树），不专属任何 BC。与 {@link ClassificationModel} 同侧：v4 分类元数据重建前留在 bridge-v3，直读实现在
 * {@code classification.hierarchy.V3TypeHierarchySource}。
 *
 * <p>
 * <b>缺失兜底</b>：实现缺失（测试 / 无元数据环境）或返回 {@code null} 时，调用方按自身常量表兜底（需求侧见 {@code RequirementClassSchema}）。
 */
public interface ClassificationHierarchySource {

    /**
     * 分类的父节点 type。
     *
     * @param type
     *            分类 type（可空）
     * @return 父节点 type；根节点 / 节点不存在 / type 为空返回 {@code null}
     */
    Long parentOf(Long type);
}
