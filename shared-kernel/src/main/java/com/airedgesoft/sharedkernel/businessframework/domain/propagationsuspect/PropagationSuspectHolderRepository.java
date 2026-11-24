package com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect;

import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId;

import java.util.List;

/**
 * 父仓储端口（⛁ · Shared Kernel 领域层）：传播怀疑持有者的读写接缝。
 *
 * <p>
 * 每个关系仓储（引用、关联、将来 BOM）继承本接口（触发口径 8「关系行访问走父仓储链」）；编排通过容器注入的 {@code List<PropagationSuspectHolderRepository>}
 * 按上游／下游／行标识取行、批量存行——新增一种关系只加一个实现，公共代码零改动。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：公共传播怀疑服务（标记／清标记）经本端口访问各关系行，不依赖任何具体 BC 的仓储类型（边界 5：公共能力不反依赖关系聚合）。
 *
 * @param <T>
 *            持有者聚合类型（如 {@code Reference}）
 */
public interface PropagationSuspectHolderRepository<T extends PropagationSuspectHolder> {

    /**
     * 按上游对象取行（该对象是被引用方：targetId 侧）。
     *
     * @param upstreamObjectId
     *            上游对象 id
     * @return 相关行集合（空集合法）
     */
    List<T> findUpstreamRows(Long upstreamObjectId);

    /**
     * 按下游对象取行（该对象是引用方：sourceId 侧）。
     *
     * @param downstreamObjectId
     *            下游对象 id
     * @return 相关行集合（空集合法）
     */
    List<T> findDownstreamRows(Long downstreamObjectId);

    /**
     * 按行标识批量取行（清标记用；缺失的 id 缺席）。
     *
     * @param rowIds
     *            关系行标识集合
     * @return 命中的行集合
     */
    List<T> findRowsByIds(List<RelationRowId> rowIds);

    /**
     * 批量保存行（聚合整体写入；含怀疑状态替换后的落库）。
     *
     * @param rows
     *            待保存的行集合
     */
    void save(List<T> rows);
}
