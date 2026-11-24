package com.airedgesoft.sharedkernel.productspace.application;

import com.airedgesoft.lib.exception.DomainException;

/**
 * 产品空间成员端口（⛁）：创建守卫的「所有者归属产品空间」裁决，判据数据在需求聚合之外（产品空间域成员关系）。
 *
 * <p>
 * 对齐 v3 {@code AddAction#checkOwnerInProductSpace}（2026-09-23 用户拍板：跨聚合校验完整落，经端口加方法不加类）：
 *
 * <ul>
 * <li>未指定产品空间（空 / 0）→ 放行（v3 同款）</li>
 * <li>未指定所有者 → 放行（v3 同款——Owner 后续默认当前用户，操作人上下文随操作人轮）</li>
 * <li>所有者非空且不在产品空间成员内 → {@link DomainException}</li>
 * </ul>
 *
 * <p>
 * ⚠️ 挂账：v3 还有判据前提——仅当「职能表启用且 Owner 属性列表配置为『产品空间内所有用户』」时才校验；该判据需读 Owner 属性 {@code enumList} 配置，sdk 层
 * {@code IInnerAttribute} 未暴露（bridge 依赖红线不可读）→ 本端口当前对非空 Owner 一律校验归属； v4 创建链操作人未落前 Owner 恒空、校验天然放行（与 v3
 * 现网行为一致），前提判据随操作人轮 / 分类元数据轮一起补。
 */
public interface ProductSpaceMemberService {

    /**
     * 所有者归属产品空间裁决（v3 {@code checkOwnerInProductSpace} 口径，判据前提见类注释挂账）。
     *
     * @param type
     *            业务对象分类 type（判据前提用；当前实现暂不消费）
     * @param workSpaceId
     *            产品空间 id
     * @param ownerId
     *            所有者 id；空放行
     */
    void assertOwnerInProductSpace(Long type, Long workSpaceId, Long ownerId);
}
