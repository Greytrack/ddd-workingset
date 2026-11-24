package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;

/**
 * 分类模型端口（过渡期契约）：分类元模型的唯一读取入口。
 *
 * <p>
 * <b>归位历史</b>（用户拍板）：v4 分类元数据尚未重建，因此本端口<b>不在</b>共享内核，而是留在 bridge-v3，与过渡模型 {@link ClassSnapshot} /
 * {@code AttributeSnapshot} 同侧；共享内核只保留消费方所需的只读契约 （{@code domain.businessobject.ClassificationFacts} /
 * {@code domain.businessobject.AttributeFacts}），由 {@link ClassSnapshot} 实现。
 *
 * <p>
 * <b>实现归属</b>：v3 直读实现在本模块 {@code classification.metadata.V3ClassificationModel}——元数据取数、启用/引用过滤、
 * 主属性作用域解析、创建期默认值解析、列表项键解析全部在实现内完成；消费方只拿到 {@link ClassSnapshot}。
 *
 * <p>
 * <b>退出条件</b>：v4 分类元数据重建后，本端口随实现一起由 v4 自有元数据模型替代（届时消费方只换装配）。
 */
public interface ClassificationModel {

    /**
     * 分类是否启用（创建守卫判据，v3 {@code AirSubClass#getEnabled} 口径：未置位视为启用）。
     *
     * @param type
     *            分类 type
     * @return true=可创建
     */
    boolean isEnabled(Long type);

    /**
     * 分类视图（可写属性定义 + 主属性列 + 子项页签启用位）。
     *
     * @param type
     *            分类 type（可空：返回空视图）
     * @return 分类视图；无元数据时返回 {@link ClassSnapshot#empty(Long)}
     */
    ClassSnapshot describe(Long type);

    /**
     * 列表取值 → 存储用 {@code itemKey}（v3 {@code AddAction#getItemKey} 口径）。
     *
     * <p>
     * 入参已是存在的 itemKey → 原值；否则按 apiId 反查并转 itemKey；两者都取不到 → 原值。
     *
     * @param listId
     *            列表 id（{@code AttributeSnapshot#getEnumList()}，可空）
     * @param value
     *            原始取值（可空）
     * @return 存储用 itemKey；无法解析时返回原值
     */
    String toItemKey(Long listId, String value);

    /**
     * 版次记录页签 id（v3 {@code AirConstants.TableCommon.REVISION_RECORDS} 页签）：版次行 {@code tab_id} 列的落库值。
     *
     * <p>
     * 版次行 {@code tab_id} 是 v3 页签模型的概念（v4 无页签）；为了让 v3 界面/接口能正确读取 v4 写入的版次行， 该列仍按 v3 元数据取页签 id 写入——取数走本端口（bridge
     * 直读元数据），不把 v3 类型泄到应用层。
     *
     * @param type
     *            分类 type（可空）
     * @return 版次记录页签 id；无元数据 / 未配页签返回 {@code null}
     */
    Long revisionTabId(Long type);

    /**
     * 关联关系页签 id（v3 {@code AirConstants.TableAssociation.API_ID = "Associations"}）：关联行 {@code tab_id} 列的落库值。
     *
     * <p>
     * 与 {@link #revisionTabId(Long)} 同口径：v4 无页签模型，但为了让 v3 界面/接口能正确读取 v4 写入的关联行， 该列仍按 v3 分类元数据取页签 id 写入。
     *
     * @param type
     *            分类 type（可空）
     * @return 关联关系页签 id；无元数据 / 未配页签返回 {@code null}
     */
    Long associationTabId(Long type);
}
