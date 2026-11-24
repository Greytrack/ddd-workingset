package com.airedgesoft.sharedkernel.businessframework.domain.reference;

import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.RowMark;
import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspect;
import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspectHolder;
import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId;
import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 聚合根（★）：引用行（Reference，一条引用＝一个聚合根；引用稿场景 §1.1、类图 §二）。
 *
 * <p>
 * <b>身份诞生即定形</b>（五-3）：{@code sourceId（引用方/下游）＋ attributeApiId ＋ targetId（被引用/上游）＋ targetRevision}
 * 四元组，任何一项要变＝删旧＋加新——聚合<b>没有</b> {@code setTarget} / {@code setTargetRevision} / {@code setPropagationType}（口径 2）。
 *
 * <p>
 * <b>引用条数不随版次增长</b>（口径 1）：签出只在聚合内追加一条 {@link ReferenceRevisionRecord}（「不变」），行本身不复制； 历史版次求值在仓储侧按「{@code revision ≤ N}
 * 的最后一条记录」判定（口径 11），聚合只回答单条记录与记录集的问题。
 *
 * <p>
 * <b>传播怀疑</b>：实现 {@link PropagationSuspectHolder}（触发口径 8 父仓储链）；标记落行由聚合方法完成 （10 号稿 §三-3），「不传播」在值对象内部原样返回。
 *
 * <p>
 * <b>合并基线</b>（用户裁定 2，推翻口径 6）：{@code PropagationSuspect} 持 upstream/downstreamMergeRevision， 创建时初始化—— upstream=目标
 * revision（跟随最新→1）、downstream=宿主 revision（可空→1），对齐 v3 {@code AddAction:1094-1101}。
 *
 * <p>
 * <b>attributeApiId</b>（裁决 7）：本轮不读 {@code air_sys_node}，缺省 {@link #DEFAULT_ATTRIBUTE_API_ID}（"Reference1"）； 契约保留 apiId
 * 字段，将来接 node 配置零迁移。
 */
public class Reference implements PropagationSuspectHolder {

    /** 引用属性 apiId 缺省值（裁决 7：本轮不读 air_sys_node；契约保留字段，将来接配置零迁移）。 */
    public static final String DEFAULT_ATTRIBUTE_API_ID = "Reference1";

    /** 单次引用操作上限（口径 14；与关联同一阈值——守卫层断言，聚合不重复判）。 */
    public static final int BATCH_LIMIT = 200;

    private final Long referenceId;
    private final Long sourceId;
    private final String attributeApiId;
    private final Long targetId;
    private final TargetRevision targetRevision;
    private final ReferenceType referenceType;
    private PropagationSuspect suspect;
    private List<ReferenceRevisionRecord> revisionRecords;

    private Reference(Long referenceId, Long sourceId, String attributeApiId, Long targetId,
            TargetRevision targetRevision, ReferenceType referenceType, PropagationSuspect suspect,
            List<ReferenceRevisionRecord> revisionRecords) {
        this.referenceId = Objects.requireNonNull(referenceId, "referenceId");
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.attributeApiId = Objects.requireNonNull(attributeApiId, "attributeApiId");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.targetRevision = Objects.requireNonNull(targetRevision, "targetRevision");
        this.referenceType = Objects.requireNonNull(referenceType, "referenceType");
        this.suspect = Objects.requireNonNull(suspect, "suspect");
        this.revisionRecords = new ArrayList<>(Objects.requireNonNull(revisionRecords, "revisionRecords"));
    }

    /**
     * 挂出引用（构造即记一条本版次「新增」记录——口径 1、六-6：行与记录同事务落库）。
     *
     * <p>
     * 传播类型由应用服务按「目标版次两态」装配后传入：跟随最新取命令携带值；钉住版次＝「不传播」（口径 5）—— 聚合不做推导。
     *
     * @param sourceId
     *            引用方需求 id（下游）
     * @param attributeApiId
     *            引用属性 apiId（裁决 7：本轮缺省 {@link #DEFAULT_ATTRIBUTE_API_ID}，由应用服务填）
     * @param targetId
     *            被引用对象 id（上游）
     * @param targetRevision
     *            目标版次两态
     * @param sourceRevision
     *            引用方当前版次号（本版次记录记「新增」）
     * @param propagationType
     *            传播类型（应用服务装配后的快照）
     * @param targetRevisionNumber
     *            目标版次号（跟随最新传 null→1；用于上游合并基线初始化）
     * @param referenceType
     *            引用类型（应用服务按目标 Tracker 共享性装配，裁决 4）
     * @return 新引用聚合
     */
    public static Reference create(Long sourceId, String attributeApiId, Long targetId, TargetRevision targetRevision,
            int sourceRevision, Long propagationType, Integer targetRevisionNumber, ReferenceType referenceType) {
        long upstreamBaseline = targetRevisionNumber == null ? 1L : targetRevisionNumber.longValue();
        long downstreamBaseline = 1L;
        return new Reference(Ids.nextId(), sourceId, attributeApiId, targetId, targetRevision, referenceType,
                PropagationSuspect.create(sourceId, targetId, propagationType, upstreamBaseline, downstreamBaseline),
                List.of(new ReferenceRevisionRecord(sourceRevision, RowMark.ADDED)));
    }

    /**
     * 由持久化状态重建（仓储装配专用）：不校验、不发事件、不补记录。
     *
     * @param referenceId
     *            引用 id
     * @param sourceId
     *            引用方 id
     * @param attributeApiId
     *            引用属性 apiId
     * @param targetId
     *            被引用对象 id
     * @param targetRevision
     *            目标版次两态
     * @param referenceType
     *            引用类型
     * @param suspect
     *            传播怀疑状态
     * @param revisionRecords
     *            版次记录集（自然键=版次，每版次至多一条——重复在重建处拒绝）
     * @return 重建的聚合
     */
    public static Reference reconstitute(Long referenceId, Long sourceId, String attributeApiId, Long targetId,
            TargetRevision targetRevision, ReferenceType referenceType, PropagationSuspect suspect,
            List<ReferenceRevisionRecord> revisionRecords) {
        List<ReferenceRevisionRecord> records = new ArrayList<>(Objects.requireNonNull(revisionRecords));
        records.sort(Comparator.comparingInt(ReferenceRevisionRecord::revision));
        for (int i = 1; i < records.size(); i++) {
            if (records.get(i).revision() == records.get(i - 1).revision()) {
                throw new IllegalStateException(
                        "引用版次记录重复: referenceId=" + referenceId + ", R" + records.get(i).revision());
            }
        }
        return new Reference(referenceId, sourceId, attributeApiId, targetId, targetRevision, referenceType, suspect,
                records);
    }

    // ------------------------------------------------------------------
    // 读面
    // ------------------------------------------------------------------

    /** 引用 id（聚合身份）。 */
    public Long referenceId() {
        return referenceId;
    }

    /** 引用方需求 id（下游）。 */
    public Long sourceId() {
        return sourceId;
    }

    /** 引用属性 apiId（裁决 7 缺省 Reference1）。 */
    public String attributeApiId() {
        return attributeApiId;
    }

    /** 被引用对象 id（上游）。 */
    public Long targetId() {
        return targetId;
    }

    /** 目标版次两态。 */
    public TargetRevision targetRevision() {
        return targetRevision;
    }

    /** 引用类型（裁决 4）。 */
    public ReferenceType referenceType() {
        return referenceType;
    }

    /** 版次记录集（只读视图，按版次升序）。 */
    public List<ReferenceRevisionRecord> revisionRecords() {
        return List.copyOf(revisionRecords);
    }

    // ------------------------------------------------------------------
    // 版次记录动作（只在应用服务按场景调用；口径 2/3）
    // ------------------------------------------------------------------

    /**
     * 签出追加一条「不变」记录；该版次已有记录则不重复追加（幂等，六-2 重复投递安全）。
     *
     * @param sourceRevision
     *            签出后的引用方版次号
     * @return 实际追加=true（重复投递=false，调用方按此过滤保存）
     */
    public boolean markUnchanged(int sourceRevision) {
        if (hasRecordOf(sourceRevision)) {
            return false;
        }
        revisionRecords.add(new ReferenceRevisionRecord(sourceRevision, RowMark.UNCHANGED));
        return true;
    }

    /**
     * 删除引用：把本版次那条记录改成「失效」，旧版次记录与行本身不动（口径 2）。
     *
     * @param sourceRevision
     *            引用方当前版次号
     */
    public void markInvalidated(int sourceRevision) {
        requireRecordOf(sourceRevision);
        replaceMark(sourceRevision, RowMark.INVALIDATED);
    }

    /**
     * 取消签出回滚：删掉本版次记录（回滚不留痕）；是否整行删除由应用服务用 {@link #hasEffectiveRecord()} 判（口径 3）。 已无该版次记录时为空操作（幂等）。
     *
     * @param sourceRevision
     *            被丢弃的工作版次号
     */
    public void removeRowMark(int sourceRevision) {
        revisionRecords.removeIf(record -> record.belongsToRevision(sourceRevision));
    }

    /** 该行在本版次之前是否已有记录——决定「留失效痕迹」还是「直接丢弃」（口径 2）。 */
    public boolean hasRecordBefore(int sourceRevision) {
        return revisionRecords.stream().anyMatch(record -> record.isBefore(sourceRevision));
    }

    /** 记录集里是否还存在有效记录（非失效）——整行删除的唯一判据（口径 3；六-1：不是「当前版次无效」）。 */
    public boolean hasEffectiveRecord() {
        return revisionRecords.stream().anyMatch(ReferenceRevisionRecord::isEffective);
    }

    /** 指定版次下的有效标记（新增/不变）；无该版次记录返回空。 */
    public boolean effectiveAt(int revision) {
        return revisionRecords.stream().filter(record -> record.belongsToRevision(revision)).findFirst()
                .map(ReferenceRevisionRecord::isEffective).orElse(false);
    }

    private boolean hasRecordOf(int sourceRevision) {
        return revisionRecords.stream().anyMatch(record -> record.belongsToRevision(sourceRevision));
    }

    private void requireRecordOf(int sourceRevision) {
        if (!hasRecordOf(sourceRevision)) {
            throw new IllegalStateException("引用无本版次记录: referenceId=" + referenceId + ", R" + sourceRevision);
        }
    }

    private void replaceMark(int sourceRevision, RowMark mark) {
        List<ReferenceRevisionRecord> replaced = new ArrayList<>(revisionRecords.size());
        for (ReferenceRevisionRecord record : revisionRecords) {
            replaced.add(record.belongsToRevision(sourceRevision)
                    ? new ReferenceRevisionRecord(record.revision(), mark)
                    : record);
        }
        revisionRecords = replaced;
    }

    // ------------------------------------------------------------------
    // PropagationSuspectHolder（公共传播怀疑链路）
    // ------------------------------------------------------------------

    @Override
    public RelationRowId relationRowId() {
        return RelationRowId.of(RelationType.REFERENCE, referenceId);
    }

    @Override
    public PropagationSuspect propagationSuspect() {
        return suspect;
    }

    /** 按变化对象落标记（方向与传播类型由值对象自答；「不传播」原样返回——口径 4）。 */
    @Override
    public void markSuspect(Long changedObjectId) {
        this.suspect = suspect.markSuspectFor(changedObjectId);
    }

    /** 清除两端标记（裁决完成；幂等）。 */
    @Override
    public void clearSuspect() {
        this.suspect = suspect.clearSuspects();
    }
}
