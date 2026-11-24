package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order;

/**
 * 接口：可排序树节点（跨域 SPI）。
 *
 * <p>
 * 以「父边 + 位置值」关联的树节点通用能力；需求等任何带父边与位置值的对象均可实现。落位写入口只有 {@link #relocateTo(Long, OrderIndex)} 一处。
 *
 * <p>
 * <b>容器成员即节点自身</b>（2026-11 用户拍板）：树遍历取回的成员就是实现本接口的领域对象，位置值与身份直接从对象读（ {@link #getId()}／
 * {@link #getOrderIndex()}），<b>不另建成员视图 DTO</b>——避免在 shared-kernel 里再长一个与聚合状态平行的影子类型。
 */
public interface OrderTreeable {

    /**
     * 对象身份（容器成员比对 / 移动者幂等摘除用）。
     *
     * <p>
     * 与 {@code BusinessObject#getId()} 同名同义：树节点即业务对象自身，不再另立 {@code objectId} 别名， 容器成员直接用聚合根身份比对。
     */
    Long getId();

    /** 父边：父对象 id；{@code null}＝根节点。 */
    Long getParentId();

    /** 位置值（兄弟排序）。 */
    OrderIndex getOrderIndex();

    /** 结构字段唯一写入口（一次性重定位父边与位置值）。 */
    void relocateTo(Long parentId, OrderIndex orderIndex);
}
