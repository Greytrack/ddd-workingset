package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderIndex;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderPolicy;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderTreeable;

import java.util.List;

/**
 * 落位接缝（SPI · 跨域通用 · 纯 · 无返回值，shared-kernel）：算值（{@link OrderPolicy}）＋ 调 {@code relocateTo} 落位。
 *
 * <p>
 * 跨域通用：任何以「父边 + 位置值」组织成员的容器（需求树、目录树、分类树等）共用本接缝。三个具名入口表达落位意图；取数与保存由各 BC 应用层完成，守卫由各 BC 前置。
 *
 * <p>
 * <b>容器成员＝节点自身</b>（2026-11 用户拍板）：入参的成员列表直接是 {@link OrderTreeable} 领域对象（含已删除），位置值经 {@link OrderTreeable#getOrderIndex()}
 * 读取，身份经 {@link OrderTreeable#getId()} 比对——不再有成员视图 DTO 中转。
 *
 * <p>
 * <b>框架纪律</b>：本类属 shared-kernel 领域层「业务对象 + 多版次」包，包内零框架技术依赖——<b>不挂</b> {@code @Component} 等框架注解，装配由各 BC 的
 * {@code infrastructure/config} 以 {@code @Bean} 声明（沿 {@code RequirementDomainConfiguration} 口径）。
 */
public class PlacementService {

    private final OrderPolicy orderPolicy;

    public PlacementService(OrderPolicy orderPolicy) {
        this.orderPolicy = orderPolicy;
    }

    /** 新建子项：落到容器末尾（一次定型父边＋位置值）。 */
    public void createChild(OrderTreeable node, Long parentId, List<OrderTreeable> members) {
        List<OrderIndex> indexes = orderPolicy.allocateAtEnd(List.of(node.getId()), members);
        node.relocateTo(parentId, indexes.get(0));
    }

    /** 同层 / 跨层落到容器头部。 */
    public void moveToHead(List<OrderTreeable> movings, Long parentId, List<OrderTreeable> members) {
        apply(movings, parentId, orderPolicy.allocateAtHead(ids(movings), members));
    }

    /** 同层 / 跨层落到容器末尾。 */
    public void moveToEnd(List<OrderTreeable> movings, Long parentId, List<OrderTreeable> members) {
        apply(movings, parentId, orderPolicy.allocateAtEnd(ids(movings), members));
    }

    /** 落到两个成员之间。 */
    public void moveBetween(List<OrderTreeable> movings, Long parentId, Long beforeId, Long afterId,
            List<OrderTreeable> members) {
        apply(movings, parentId, orderPolicy.allocateBetween(ids(movings), beforeId, afterId, members));
    }

    private void apply(List<OrderTreeable> movings, Long parentId, List<OrderIndex> indexes) {
        for (int i = 0; i < movings.size(); i++) {
            movings.get(i).relocateTo(parentId, indexes.get(i));
        }
    }

    private static List<Long> ids(List<OrderTreeable> movings) {
        return movings.stream().map(OrderTreeable::getId).toList();
    }
}
