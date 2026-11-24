package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspect;
import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspectHolder;
import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId;
import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationType;

import java.util.*;

/**
 * 聚合根（★）：关联行（Association，一行一聚合；关联稿 §1.1、类图 §二）。
 *
 * <p>
 * <b>通用关系行</b>（用户裁定 2026-11-23）：两端带类型（{@code sourceType} / {@code targetType}），落 shared-kernel， 承载 v3
 * {@code air_inst_tab_association} 的跨类型关系语义；需求 BC 只做编排与适配。
 *
 * <p>
 * <b>端语义固定</b>：{@code sourceId}＝源=下游（v3 {@code instance_id}）、{@code targetId}＝目标=上游（v3
 * {@code related_instance_id}）；方向判定在 {@link PropagationSuspect} 内部完成。
 *
 * <p>
 * <b>合并基线</b>（用户裁定 4，推翻设计稿「关联行不持合并基线」）：{@link PropagationSuspect} 保留 upstream/downstreamMergeRevision， 创建时
 * upstream=目标版次（可空→1）、downstream=源当前版次（可空→1）；合并后由 {@link #advanceUpstreamMergeRevision} /
 * {@link #advanceDownstreamMergeRevision} 推进。
 *
 * <p>
 * <b>写入规则</b>：状态变更只能经聚合方法；值对象在聚合内部整体替换，外部无 setter 后门。
 */
public class Association implements PropagationSuspectHolder {

    /** 关联类型缺省值：依存于（v3 {@code RelationTypeEnum.DEPENDS_ON} 列表项 key；约束二-12）。 */
    public static final long DEFAULT_ASSOCIATION_TYPE = 577324844931456L;

    /** 传播类型缺省值：不传播（v3 {@code PropagateTypeEnum.NONE} 列表项 key）。 */
    public static final long DEFAULT_PROPAGATION_TYPE = PropagationSuspect.NO_PROPAGATION;

    private final Long associationId;
    private final Long sourceId;
    private final Long sourceType;
    private final Long targetId;
    private final Long targetType;
    private Long associationType;
    private PropagationSuspect suspect;
    private AssociationRule rule;
    private Map<String, FieldValue> optionalValues;

    private Association(Long associationId, Long sourceId, Long sourceType, Long targetId, Long targetType,
            Long associationType, PropagationSuspect suspect, AssociationRule rule,
            Map<String, FieldValue> optionalValues) {
        this.associationId = Objects.requireNonNull(associationId, "associationId");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.associationType = Objects.requireNonNull(associationType, "associationType");
        this.suspect = Objects.requireNonNull(suspect, "suspect");
        this.rule = rule;
        this.optionalValues = new LinkedHashMap<>(optionalValues == null ? Map.of() : optionalValues);
    }

    /**
     * 建立关联行（v3 {@code AssociationTable#applyAssociationRowDefaults} 口径：类型/传播缺省、基线初始化、标记清零）。
     *
     * @param sourceId
     *            源对象 id（下游）
     * @param sourceType
     *            源对象分类 type
     * @param targetId
     *            目标对象 id（上游）
     * @param targetType
     *            目标对象分类 type
     * @param associationType
     *            关联类型（v3 {@code relationship_type}；空→依存于）
     * @param propagationType
     *            传播类型（v3 {@code propagation_type}；空→不传播）
     * @param upstreamMergeRevision
     *            上游合并基线（目标当前版次；可空→1）
     * @param downstreamMergeRevision
     *            下游合并基线（源当前版次；可空→1）
     * @return 新关联行聚合
     */
    public static Association create(Long sourceId, Long sourceType, Long targetId, Long targetType,
            Long associationType, Long propagationType, Long upstreamMergeRevision, Long downstreamMergeRevision) {
        Long effectiveAssociationType = associationType == null ? DEFAULT_ASSOCIATION_TYPE : associationType;
        Long effectivePropagationType = propagationType == null ? DEFAULT_PROPAGATION_TYPE : propagationType;
        return new Association(Ids.nextId(), sourceId, sourceType, targetId, targetType, effectiveAssociationType,
                PropagationSuspect.create(sourceId, targetId, effectivePropagationType, upstreamMergeRevision,
                        downstreamMergeRevision),
                null, Map.of());
    }

    /**
     * 由持久化状态重建（仓储装配专用）：不校验、不发事件、不补齐默认值。
     *
     * @param associationId
     *            关联行 id
     * @param sourceId
     *            源对象 id
     * @param sourceType
     *            源对象分类 type
     * @param targetId
     *            目标对象 id
     * @param targetType
     *            目标对象分类 type
     * @param associationType
     *            关联类型
     * @param suspect
     *            传播怀疑状态（含合并基线）
     * @param rule
     *            规则（可空）
     * @param optionalValues
     *            可选属性值（键＝落库列名；可空）
     * @return 重建的聚合
     */
    public static Association reconstitute(Long associationId, Long sourceId, Long sourceType, Long targetId,
            Long targetType, Long associationType, PropagationSuspect suspect, AssociationRule rule,
            Map<String, FieldValue> optionalValues) {
        return new Association(associationId, sourceId, sourceType, targetId, targetType, associationType, suspect,
                rule, optionalValues);
    }

    // ------------------------------------------------------------------
    // 读面
    // ------------------------------------------------------------------

    /** 关联行 id（聚合身份）。 */
    public Long associationId() {
        return associationId;
    }

    /** 源对象 id（下游）。 */
    public Long sourceId() {
        return sourceId;
    }

    /** 源对象分类 type。 */
    public Long sourceType() {
        return sourceType;
    }

    /** 目标对象 id（上游）。 */
    public Long targetId() {
        return targetId;
    }

    /** 目标对象分类 type。 */
    public Long targetType() {
        return targetType;
    }

    /** 关联类型（v3 {@code relationship_type}）。 */
    public Long associationType() {
        return associationType;
    }

    /** 规则（0..1）。 */
    public Optional<AssociationRule> getRule() {
        return Optional.ofNullable(rule);
    }

    /** 可选属性值（只读视图；键＝落库列名）。 */
    public Map<String, FieldValue> optionalValues() {
        return Map.copyOf(optionalValues);
    }

    // ------------------------------------------------------------------
    // 行内状态变更（批量修改关联行的落点）
    // ------------------------------------------------------------------

    /**
     * 改关联类型（约束：换目标＝删除＋新增；关联类型可改）。
     *
     * @param associationType
     *            新关联类型
     */
    public void setAssociationType(Long associationType) {
        this.associationType = Objects.requireNonNull(associationType, "associationType");
    }

    /**
     * 改传播类型（v3 {@code propagation_type}；传播怀疑值对象内部整体替换）。
     *
     * @param propagationType
     *            新传播类型
     */
    public void setPropagationType(Long propagationType) {
        this.suspect = suspect.withPropagationType(Objects.requireNonNull(propagationType, "propagationType"));
    }

    /**
     * 整体设置规则（与 {@link #removeRule()} 互斥；规则渲染名随值对象携带）。
     *
     * @param rule
     *            规则值对象
     */
    public void assignRule(AssociationRule rule) {
        this.rule = Objects.requireNonNull(rule, "rule");
    }

    /** 整体删除规则（v3 {@code deleteRelationRule} 口径）。 */
    public void removeRule() {
        this.rule = null;
    }

    /**
     * 整值替换可选属性：未启用列静默丢弃（约束二-5；调用方误传不报错）。
     *
     * @param values
     *            提交值（键＝落库列名）
     * @param enabledAttributes
     *            页签启用的属性列集合（空/null＝全部丢弃）
     */
    public void setOptionalValues(Map<String, FieldValue> values, Set<String> enabledAttributes) {
        Map<String, FieldValue> source = values == null ? Map.of() : values;
        Set<String> enabled = enabledAttributes == null ? Set.of() : enabledAttributes;
        Map<String, FieldValue> filtered = new LinkedHashMap<>();
        source.forEach((column, value) -> {
            if (enabled.contains(column) && value != null) {
                filtered.put(column, value);
            }
        });
        this.optionalValues = filtered;
    }

    /**
     * 合并后推进上游合并基线（v3 {@code updateUpstreamMergeRevision}；目标端被合并写到的版次）。
     *
     * @param revision
     *            新上游基线版次号
     */
    public void advanceUpstreamMergeRevision(Long revision) {
        this.suspect = suspect.advanceUpstreamMergeRevision(revision);
    }

    /**
     * 合并后推进下游合并基线（v3 {@code updateDownstreamMergeRevision}；源端被合并写到的版次）。
     *
     * @param revision
     *            新下游基线版次号
     */
    public void advanceDownstreamMergeRevision(Long revision) {
        this.suspect = suspect.advanceDownstreamMergeRevision(revision);
    }

    // ------------------------------------------------------------------
    // PropagationSuspectHolder（公共传播怀疑链路）
    // ------------------------------------------------------------------

    @Override
    public RelationRowId relationRowId() {
        return RelationRowId.of(RelationType.ASSOCIATION, associationId);
    }

    @Override
    public PropagationSuspect propagationSuspect() {
        return suspect;
    }

    /** 按变化对象落标记（方向与传播类型由值对象自答；「不传播」原样返回）。 */
    @Override
    public void markSuspect(Long changedObjectId) {
        this.suspect = suspect.markSuspectFor(changedObjectId);
    }

    /** 清除两端标记（幂等）。 */
    @Override
    public void clearSuspect() {
        this.suspect = suspect.clearSuspects();
    }
}
