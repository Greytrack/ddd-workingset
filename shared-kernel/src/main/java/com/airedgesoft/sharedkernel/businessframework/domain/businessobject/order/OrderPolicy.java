package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.PlacementService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 落位策略（纯算法，无 IO，shared-kernel）：按容器成员整批计算位置值。位置值编解码 / 比较口径收敛在 {@link OrderIndex}。
 *
 * <p>
 * 跨域通用：任何以「父边 + 位置值」组织成员的容器（需求树、目录树、分类树等）共用本策略；取值与落位由各 BC 的 {@link PlacementService} 编排，本类只算值、不碰对象。
 *
 * <p>
 * <b>容器成员＝节点自身</b>（2026-11 用户拍板）：入参直接是 {@link OrderTreeable} 领域对象（含已删除），身份取 {@link OrderTreeable#getId()}、 位置值取
 * {@link OrderTreeable#getOrderIndex()}——不再有 {@code OrderMember} 成员视图 DTO。
 *
 * <p>
 * 位置值口径与 v3 同源：head/end 沿 lexorank 链 prev/next 推进，between 取 {@code LexoRank#between}，不重排既有成员位置值。
 */
public class OrderPolicy {

    /** 容器头部落位：取最小位值之前 n 个（lexorank 沿链回退）。 */
    public List<OrderIndex> allocateAtHead(List<Long> movingIds, List<OrderTreeable> members) {
        List<OrderTreeable> rest = without(members, movingIds);
        OrderIndex first = rest.stream().map(OrderTreeable::getOrderIndex).filter(Objects::nonNull)
                .min(OrderIndex::compareTo).orElse(null);
        int count = movingIds.size();
        List<OrderIndex> result = new ArrayList<>(count);
        OrderIndex cursor = first;
        // 从头部往前推 count 个后顺序填回（保证结果仍递增）
        List<OrderIndex> reversed = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cursor = cursor == null ? OrderIndex.lexoInitial() : cursor.prev();
            reversed.add(cursor);
        }
        for (int i = count - 1; i >= 0; i--) {
            result.add(reversed.get(i));
        }
        return result;
    }

    /** 容器尾部落位：取最大位值之后 n 个（lexorank 沿链推进，与 v3 追加排序同口径）。 */
    public List<OrderIndex> allocateAtEnd(List<Long> movingIds, List<OrderTreeable> members) {
        List<OrderTreeable> rest = without(members, movingIds);
        OrderIndex last = rest.stream().map(OrderTreeable::getOrderIndex).filter(Objects::nonNull)
                .max(OrderIndex::compareTo).orElse(null);
        int count = movingIds.size();
        List<OrderIndex> result = new ArrayList<>(count);
        OrderIndex cursor = last;
        for (int i = 0; i < count; i++) {
            cursor = cursor == null ? OrderIndex.lexoInitial() : cursor.next();
            result.add(cursor);
        }
        return result;
    }

    /**
     * 两邻居之间落位：在 before / after 之间取 n 个位置值（lexorank 沿链 between，与 v3 BEFORE/AFTER 同口径）。
     *
     * <p>
     * 边界裁决：before 缺失＝容器头部（after 前驱起向右逼近）；after 缺失＝容器尾部（before 后继起沿链推进）；两者皆缺＝空容器（初值起沿链推进）。
     */
    public List<OrderIndex> allocateBetween(List<Long> movingIds, Long beforeId, Long afterId,
            List<OrderTreeable> members) {
        List<OrderTreeable> rest = without(members, movingIds);
        OrderIndex before = indexOf(rest, beforeId);
        OrderIndex after = indexOf(rest, afterId);
        int count = movingIds.size();
        List<OrderIndex> result = new ArrayList<>(count);
        if (before != null && after != null) {
            // 左/右邻居不同值才可 between（与 v3 先判 !prev.equals(reference) 再 between 同口径），同值回退到右邻居前驱
            OrderIndex cursor = before.value().equals(after.value()) ? after.prev() : before.between(after);
            result.add(cursor);
            for (int i = 1; i < count; i++) {
                cursor = cursor.between(after);
                result.add(cursor);
            }
        } else if (after != null) {
            // 头部边界：after 之前，取 after 前驱后向右逼近（与 v3 BEFORE 无左邻居同口径）
            OrderIndex cursor = after.prev();
            result.add(cursor);
            for (int i = 1; i < count; i++) {
                cursor = cursor.between(after);
                result.add(cursor);
            }
        } else if (before != null) {
            // 尾部边界：before 之后，沿链推进（与 v3 AFTER 无右邻居同口径）
            OrderIndex cursor = before.next();
            for (int i = 0; i < count; i++) {
                result.add(cursor);
                cursor = cursor.next();
            }
        } else {
            // 空容器：初值起沿链推进
            OrderIndex cursor = OrderIndex.lexoInitial();
            for (int i = 0; i < count; i++) {
                result.add(cursor);
                cursor = cursor.next();
            }
        }
        return result;
    }

    private static List<OrderTreeable> without(List<OrderTreeable> members, List<Long> movingIds) {
        if (members == null) {
            return List.of();
        }
        if (movingIds == null || movingIds.isEmpty()) {
            return members;
        }
        return members.stream().filter(member -> !movingIds.contains(member.getId())).toList();
    }

    private static OrderIndex indexOf(List<OrderTreeable> members, Long objectId) {
        if (objectId == null) {
            return null;
        }
        return members.stream().filter(member -> Objects.equals(member.getId(), objectId))
                .map(OrderTreeable::getOrderIndex).filter(Objects::nonNull).findFirst().orElse(null);
    }
}
