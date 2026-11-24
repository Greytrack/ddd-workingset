package com.airedgesoft.sharedkernel.businessframework.infrastructure.association;

import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;

import java.util.Collection;
import java.util.Map;

/**
 * 关联行宿主冗余列上下文端口：按两端对象 id 装配 v3 冗余列事实（不进领域；与引用行 {@code ReferenceSourceContextProvider} 同口径）。
 *
 * <p>
 * 实现由消费方 BC 提供（如 v4-alm 读需求概要）：{@code common_id/instance_type/work_space/workingset_id/tracker} 与对端同名列、
 * 两端编号（规则底座用）。缺失值允许为 {@code null}（v3 同列宽容）。
 */
public interface AssociationContextProvider {

    /**
     * 装配关联行上下文。
     *
     * @param sourceId
     *            源对象 id（下游）
     * @param targetId
     *            目标对象 id（上游）
     * @return 上下文快照
     */
    AssociationContext load(Long sourceId, Long targetId);

    /**
     * 批量装配（一次 lite 快照查询；按关联行 id 返回）。
     *
     * rows 关联行集合 行 id -> 上下文
     */
    Map<Long, AssociationContext> loadAll(Collection<Association> rows);

    /**
     * 冗余列上下文快照。
     *
     * @param sourceCommonId
     *            源对象公共根 id
     * @param sourceWorkSpace
     *            源对象所属产品空间
     * @param sourceWorkingsetId
     *            源对象所属工作集
     * @param sourceTracker
     *            源对象跟踪分类
     * @param sourceNumber
     *            源对象编号（规则底座）
     * @param targetCommonId
     *            目标对象公共根 id
     * @param targetWorkSpace
     *            目标对象所属产品空间
     * @param targetWorkingsetId
     *            目标对象所属工作集
     * @param targetTracker
     *            目标对象跟踪分类
     * @param targetNumber
     *            目标对象编号（规则底座）
     * @param sourcePhaseAttributeId
     *            源端生命周期相位属性 id（规则条件/结果列；可空）
     * @param targetPhaseAttributeId
     *            目标端生命周期相位属性 id（规则条件/结果列；可空）
     * @param sourceTabId
     *            源端关联关系页签 id（v3 {@code tab_id} 列；可空）
     */
    record AssociationContext(Long sourceCommonId, Long sourceWorkSpace, Long sourceWorkingsetId, Long sourceTracker,
            String sourceNumber, Long targetCommonId, Long targetWorkSpace, Long targetWorkingsetId, Long targetTracker,
            String targetNumber, Long sourcePhaseAttributeId, Long targetPhaseAttributeId, Long sourceTabId) {
    }
}
