package com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect;

import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId;

/**
 * 角色接口：传播怀疑持有者（PropagationSuspectHolder，Shared Kernel「对象关系」公共契约）。
 *
 * <p>
 * 由各关系聚合（引用、关联、将来 BOM）实现——公共传播怀疑链路只认本接口，不认识具体聚合（触发口径 8「父仓储链」）。 状态变更只能经聚合方法（引用稿「写路径原则」）：实现方在 {@link #markSuspect(Long)}
 * / {@link #clearSuspect()} 内部替换自身的 {@link PropagationSuspect} 值对象，外部不得直接改值。
 *
 * <p>
 * 「不传播」行的过滤在值对象内部（{@code PropagationSuspect#markSuspectFor} 原样返回），实现方无需重复判断。
 */
public interface PropagationSuspectHolder {

    /**
     * 自身的关系行标识（转换只在聚合内发生）。
     *
     * @return {@code RelationRowId.of(自身类型, 自身 id)}
     */
    RelationRowId relationRowId();

    /**
     * 当前的传播怀疑状态（只读视图）。
     *
     * @return 怀疑值对象
     */
    PropagationSuspect propagationSuspect();

    /**
     * 按变化对象落标记（方向与传播类型由行自答；「不传播」原样返回）。
     *
     * @param changedObjectId
     *            发生变化的对象 id
     */
    void markSuspect(Long changedObjectId);

    /**
     * 清除两端标记（裁决完成；幂等）。
     */
    void clearSuspect();
}
