package com.airedgesoft.lib.jdbc;

import java.util.List;
import java.util.Objects;

/**
 * 仓储归组的根行与子行，独立于可变领域对象保留加载时的原始值。
 *
 * <p>
 * 领域重建可能把 NULL 解释为默认值，但行定位、版本比较及兼容协议仍需原值。 本载体只冻结仓储提供的行与列，不证明装配完整或归属正确，也不授予保存资格；这些约束由仓储声明与 Store 校验。
 */
public record AggregateRows(StoredRow root, List<StoredRow> children) {
    /**
     * 冻结聚合加载快照，保留根行和子行的原始存储值。
     *
     * @param root     已加载的非空根行，根不存在时应由读取端返回 Optional.empty
     * @param children 归属于该聚合的已加载子行；复制列表以隔离外部修改
     */
    public AggregateRows {
        // 根不存在由读取端返回 Optional.empty 表达；拒绝“有快照但没有根”的半装配状态。
        Objects.requireNonNull(root, "根行不能为空");
        // 隔离调用方对原列表的修改，防止保存前的比较依据被污染。
        children = List.copyOf(children);
    }
}
