package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

/**
 * 列表项 itemKey 解析契约（共享内核只读契约）：列表取值 → 存储用 {@code itemKey}。
 *
 * <p>
 * <b>为何独立成契约</b>：字段值装配（多选列表入参规范化）只需要「把取值转成 itemKey」这一件能力，而 itemKey 的解析口径是 v3 元数据能力（{@code ListObj#getItemByKey} /
 * {@code getItemByApiId}）——实现留在 bridge-v3（ {@code ClassificationModel#toItemKey}），共享内核只声明这一个函数面，不经手元数据来源。
 *
 * <p>
 * <b>退出条件</b>：v4 分类元数据重建后随属性值校验族一并评估去留。
 */
@FunctionalInterface
public interface ListItemKeyResolver {

    /**
     * 列表取值 → 存储用 {@code itemKey}（v3 {@code AddAction#getItemKey} 口径）。
     *
     * @param listId
     *            列表 id（可空）
     * @param value
     *            原始取值（可空）
     * @return 存储用 itemKey；无法解析时返回原值
     */
    String toItemKey(Long listId, String value);
}
