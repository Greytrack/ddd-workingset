package com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

import java.util.Objects;

/**
 * 值对象：传播怀疑（PropagationSuspect，Shared Kernel「对象关系」公共能力）。
 *
 * <p>
 * 关系行上的可怀疑状态：上游段／下游段是否已变（两态标记）＋ 传播类型 ＋ 合并基线版次（用户裁定 2026-11-18：按 v3 保留 mergeRevision，推翻设计稿口径 6「无合并基线」）。 不可变——标记变更经
 * {@link #markSuspectFor(Long)} / {@link #clearSuspects()} 返回新实例，由宿主聚合整体替换。
 *
 * <p>
 * <b>端语义固定</b>（触发口径 5）：{@code sourceId}＝引用方＝下游、{@code targetId}＝被引用＝上游；
 * 方向判定在值对象内部完成（{@code markSuspectFor(changedObjectId)} 按 id 落在哪一侧），外部不再自行判断方向。
 *
 * <p>
 * <b>「不传播」口径</b>（口径 4／5）：传播类型为「不传播」的行在 {@link #markSuspectFor(Long)} 内原样返回—— 钉住版次（目标版次两态）的行永不被事件标记，差异／合并对之不适用。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：传播怀疑是跨关系类型（引用／关联）的公共状态，落公共链路（父仓储、清标记服务）。
 *
 * @param sourceId
 *            引用方 id（下游）
 * @param targetId
 *            被引用对象 id（上游）
 * @param propagationType
 *            传播类型（v3 列表项 key：{@code PropagationTypeEnum} 四值——不传播／正向／反向／双向； 见 {@link #NO_PROPAGATION} 等常量）
 * @param upstreamChanged
 *            上游段已变（targetId 侧对象变化后置位）
 * @param downstreamChanged
 *            下游段已变（sourceId 侧对象变化后置位）
 * @param upstreamMergeRevision
 *            上游合并时钉住的版次（创建引用时初始化为目标 revision，可空→1）
 * @param downstreamMergeRevision
 *            下游合并时钉住的版次（创建引用时初始化为宿主 revision，可空→1）
 */
public record PropagationSuspect(Long sourceId, Long targetId, Long propagationType, boolean upstreamChanged,
        boolean downstreamChanged, Long upstreamMergeRevision, Long downstreamMergeRevision) {

    /** 传播类型：不传播（v3 {@code PropagationTypeEnum.NO_PROPAGATION} 列表项 key）。 */
    public static final long NO_PROPAGATION = 577449573388704L;

    /** 传播类型：正向（上游变化 → 下游行「上游段已变」；v3 FORWARD_PROPAGATION key）。 */
    public static final long FORWARD = 577449599146400L;

    /** 传播类型：反向（下游变化 → 上游行「下游段已变」；v3 BACKWARD_PROPAGATION key）。 */
    public static final long BACKWARD = 577449629473184L;

    /** 传播类型：双向（两端变化均标记；v3 BIDIRECTIONAL_PROPAGATION key）。 */
    public static final long BIDIRECTIONAL = 577449656363424L;

    public PropagationSuspect {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(propagationType, "propagationType");
    }

    /**
     * 创建初始怀疑态（挂出引用：两端均未变更；基线版次由创建方传入——v3 口径 upstream=目标 revision、downstream=宿主 revision， 可空回落 1）。
     *
     * @param sourceId
     *            引用方 id（下游）
     * @param targetId
     *            被引用对象 id（上游）
     * @param propagationType
     *            传播类型（v3 列表项 key）
     * @param upstreamMergeRevision
     *            上游基线版次（可空→1）
     * @param downstreamMergeRevision
     *            下游基线版次（可空→1）
     * @return 初始怀疑态
     */
    public static PropagationSuspect create(Long sourceId, Long targetId, Long propagationType,
            Long upstreamMergeRevision, Long downstreamMergeRevision) {
        return new PropagationSuspect(sourceId, targetId, propagationType, false, false,
                upstreamMergeRevision == null ? 1L : upstreamMergeRevision,
                downstreamMergeRevision == null ? 1L : downstreamMergeRevision);
    }

    /**
     * 按变化对象落标记（公共链路唯一标记入口）。
     *
     * <p>
     * 方向与传播类型由行自答（6 号稿触发口径 5）：变化 id＝targetId（上游变化）且传播类型为正向／双向 → 置「上游段已变」； 变化 id＝sourceId（下游变化）且传播类型为反向／双向 → 置「下游段已变」；
     * 「不传播」原样返回（口径 4）。
     *
     * @param changedObjectId
     *            发生变化的对象 id
     * @return 变更后的新实例；未命中本行两端或方向／类型不参与时原样返回
     */
    public PropagationSuspect markSuspectFor(Long changedObjectId) {
        if (changedObjectId == null || isNoPropagation()) {
            return this;
        }
        boolean upstreamSide = changedObjectId.equals(targetId);
        boolean downstreamSide = changedObjectId.equals(sourceId);
        if (!upstreamSide && !downstreamSide) {
            return this;
        }
        if (upstreamSide && (propagationType == FORWARD || propagationType == BIDIRECTIONAL)) {
            return new PropagationSuspect(sourceId, targetId, propagationType, true, downstreamChanged,
                    upstreamMergeRevision, downstreamMergeRevision);
        }
        if (downstreamSide && (propagationType == BACKWARD || propagationType == BIDIRECTIONAL)) {
            return new PropagationSuspect(sourceId, targetId, propagationType, upstreamChanged, true,
                    upstreamMergeRevision, downstreamMergeRevision);
        }
        return this;
    }

    /**
     * 清除两端标记（裁决完成；幂等）。
     *
     * @return 两态归零的新实例
     */
    public PropagationSuspect clearSuspects() {
        if (!upstreamChanged && !downstreamChanged) {
            return this;
        }
        return new PropagationSuspect(sourceId, targetId, propagationType, false, false, upstreamMergeRevision,
                downstreamMergeRevision);
    }

    /** 是否存在任一待查标记（派生展示值「是否待查」的判据，约束稿四-4）。 */
    public boolean hasChanged() {
        return upstreamChanged || downstreamChanged;
    }

    /** 是否「不传播」行（钉住版次装配产物；此类行永不被标记，差异／合并不适用）。 */
    public boolean isNoPropagation() {
        return propagationType == NO_PROPAGATION;
    }

    /**
     * 改传播类型（关联行「批量修改」的落点；两态标记与合并基线原样保留——v3 只改 {@code propagation_type} 列）。
     *
     * @param newPropagationType
     *            新传播类型（v3 列表项 key）
     * @return 变更后的新实例；同值原样返回
     */
    public PropagationSuspect withPropagationType(Long newPropagationType) {
        Objects.requireNonNull(newPropagationType, "propagationType");
        if (propagationType.equals(newPropagationType)) {
            return this;
        }
        return new PropagationSuspect(sourceId, targetId, newPropagationType, upstreamChanged, downstreamChanged,
                upstreamMergeRevision, downstreamMergeRevision);
    }

    /**
     * 推进上游合并基线（v3 {@code updateUpstreamMergeRevision} 口径；其余分量不变）。
     *
     * @param revision
     *            新基线版次号（≥1）
     * @return 变更后的新实例
     */
    public PropagationSuspect advanceUpstreamMergeRevision(Long revision) {
        long validated = requireRevision(revision);
        if (upstreamMergeRevision != null && upstreamMergeRevision == validated) {
            return this;
        }
        return new PropagationSuspect(sourceId, targetId, propagationType, upstreamChanged, downstreamChanged,
                validated, downstreamMergeRevision);
    }

    /**
     * 推进下游合并基线（v3 {@code updateDownstreamMergeRevision} 口径；其余分量不变）。
     *
     * @param revision
     *            新基线版次号（≥1）
     * @return 变更后的新实例
     */
    public PropagationSuspect advanceDownstreamMergeRevision(Long revision) {
        long validated = requireRevision(revision);
        if (downstreamMergeRevision != null && downstreamMergeRevision == validated) {
            return this;
        }
        return new PropagationSuspect(sourceId, targetId, propagationType, upstreamChanged, downstreamChanged,
                upstreamMergeRevision, validated);
    }

    private static long requireRevision(Long revision) {
        if (revision == null || revision < 1) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.REVISION_NUMBER_INVALID,
                    1, revision);
        }
        return revision;
    }
}
