package com.airedgesoft.sharedkernel.businessframework.infrastructure.reference;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.jdbc.StoredRow;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.RowMark;
import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspect;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.Reference;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceRevisionRecord;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceType;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.TargetRevision;
import com.airedgesoft.sharedkernel.businessframework.infrastructure.reference.ReferenceStorage.ReferenceColumns;

import java.util.ArrayList;
import java.util.List;

/**
 * 引用聚合存储映射（infrastructure 私有）：{@code air_inst_tab_reference} 列映射与「v4 记录集 ↔ v3 行产物」双向换算。
 *
 * <p>
 * <b>版次模型映射（考古结论 2026-11-19，对齐 v3 AddAction / UpdateAction / RevisionDataObject 产物）</b>：
 * <ul>
 * <li>v4 {@code ADDED(R)}（构造记新增）→ v3 行 {@code revision_in=R, revision_out=0, edit_row=0, flags[R]='1'}；</li>
 * <li>v4 {@code UNCHANGED(R)}（签出打记录）→ v3 无新行，{@code flags[R]='1'}（flags 位图就是 v3 的「不变」记录集——签出由对象侧 {@code tabCheckOut}
 * 对页签行统一翻位，引用侧仓储只需读）；</li>
 * <li>v4 {@code INVALIDATED(R)}（删除引用）→ v3 存活行 {@code revision_out=R}（失效版次落在行上；v3 的「删除记录行」
 * {@code revision_in=0/revision_out=R/edit_row=旧行id} 是行复制模型的产物，v4 记录模型把失效并回存活行， 读回等价：{@code revision ≤ N}
 * 最后一条记录判定结果一致）。</li>
 * </ul>
 *
 * <p>
 * <b>历史求值（口径 11）</b>：版次 N 下有效 ⇔ {@code revision_in ≤ N 且 (revision_out = 0 或 revision_out > N)}； 与「记录集中 revision ≤ N
 * 的最后一条记录非失效」等价（v4 记录集是 v3 区间+位图的等价展开）。
 *
 * <p>
 * <b>裁决落点</b>：mergeRevision 列（v3 同名列）按 v3 口径写入（upstream=目标 revision、downstream=宿主 revision）； {@code propagation_type} 存
 * Long 列表项 key；{@code reference_type} 存数字口径；宿主冗余列
 * （{@code common_id/instance_type/work_space/workingset_id/tracker/tab_id/flags...}）由仓储按引用方上下文填充，不进领域。
 */
final class ReferenceMapper {

    /** v3 页签版次可见性位图长度（v3 {@code RevisionAction.MAXFLAGSCOUNT}）。 */
    static final int FLAGS_LENGTH = 100;

    /** 系统融合页签 id（v3 {@code AirConstants.REFERENCE_TABID}）。 */
    static final long REFERENCE_TAB_ID = 999999L;

    private ReferenceMapper() {
    }

    /**
     * 从 v3 行重建引用聚合（读侧）。
     *
     * <p>
     * 记录集重建：{@code ADDED(revision_in)} ＋ {@code INVALIDATED(revision_out>0)}——「不变」由 flags 位图承载， 不落独立记录（聚合内
     * {@code markUnchanged} 幂等，签出记录由对象侧 flags 位图统一表达）。
     *
     * @param row
     *            v3 引用行（须含全部映射列）
     * @return 重建的聚合
     */
    static Reference reconstitute(StoredRow row) {
        Long referenceId = row.longValue(ReferenceColumns.ID);
        Long instanceId = row.longValue(ReferenceColumns.INSTANCE_ID);
        String attributeApiId = row.textValue(ReferenceColumns.ATTRIBUTE_API_ID);
        Long relatedInstanceId = row.longValue(ReferenceColumns.RELATED_INSTANCE_ID);
        Long relatedRevisionId = row.longValue(ReferenceColumns.RELATED_REVISION_ID);
        // int2/int4 列 JDBC 返回 Integer——统一宽容收窄（StoredRow.longValue 是严格 Long cast）
        Long relatedRevision = asLong(row.values().get(ReferenceColumns.RELATED_REVISION));
        Long propagationType = asLong(row.values().get(ReferenceColumns.PROPAGATION_TYPE));
        Integer referenceTypeCode = row.integerValue(ReferenceColumns.REFERENCE_TYPE);
        Long upstreamMerge = asLong(row.values().get(ReferenceColumns.UPSTREAM_MERGE_REVISION));
        Long downstreamMerge = asLong(row.values().get(ReferenceColumns.DOWNSTREAM_MERGE_REVISION));
        Long revisionIn = asLong(row.values().get(ReferenceColumns.REVISION_IN));
        Long revisionOut = asLong(row.values().get(ReferenceColumns.REVISION_OUT));
        Long upstreamChanged = asLong(row.values().get(ReferenceColumns.UPSTREAM_CHANGED));
        Long downstreamChanged = asLong(row.values().get(ReferenceColumns.DOWNSTREAM_CHANGED));

        TargetRevision targetRevision = relatedRevisionId == null || relatedRevisionId == 1
                ? TargetRevision.followLatest()
                : TargetRevision.pinnedTo(relatedRevision.intValue());
        List<ReferenceRevisionRecord> records = new ArrayList<>(2);
        if (revisionIn != null && revisionIn > 0) {
            records.add(new ReferenceRevisionRecord(revisionIn.intValue(), RowMark.ADDED));
        }
        if (revisionOut != null && revisionOut > 0) {
            records.add(new ReferenceRevisionRecord(revisionOut.intValue(), RowMark.INVALIDATED));
        }
        PropagationSuspect suspect = new PropagationSuspect(instanceId, relatedInstanceId,
                propagationType == null ? 0L : propagationType, upstreamChanged != null && upstreamChanged != 0,
                downstreamChanged != null && downstreamChanged != 0, upstreamMerge, downstreamMerge);
        return Reference.reconstitute(referenceId, instanceId, attributeApiId, relatedInstanceId, targetRevision,
                ReferenceType.fromCode(referenceTypeCode == null ? ReferenceType.RELATIVE.code() : referenceTypeCode),
                suspect, records);
    }

    /**
     * 版次 N 下是否有效（口径 11 的 SQL 判据：{@code revision_in ≤ N 且 (revision_out=0 或 revision_out>N)}）。
     *
     * @param revisionIn
     *            行起始版次
     * @param revisionOut
     *            行失效版次（0/空=仍有效）
     * @param revision
     *            求值版次
     * @return 有效=true
     */
    static boolean effectiveAt(Long revisionIn, Long revisionOut, int revision) {
        if (revisionIn == null || revisionIn <= 0) {
            return false;
        }
        return revisionIn <= revision && (revisionOut == null || revisionOut == 0 || revisionOut > revision);
    }

    /**
     * v3 页签版次可见性位图：在 {@code revision} 位翻 '1'（对齐 {@code RevisionAction.setRevisionFlag}）。
     *
     * @param revision
     *            版次号（0 位=首版前共享位）
     * @return 全 0 位图（由仓储再翻位）
     */
    static String emptyFlags() {
        return "0".repeat(FLAGS_LENGTH);
    }

    /**
     * 在位图指定版次位写值（索引＝版次号，对齐 v3 {@code charArray[currentRevision]}）。
     *
     * @param flags
     *            原 100 位位图
     * @param revision
     *            版次号（0–99）
     * @param value
     *            '0' 或 '1'
     * @return 新位图
     */
    static String setFlagAt(String flags, int revision, char value) {
        if (revision < 0 || revision >= FLAGS_LENGTH) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.REFERENCE_REVISION_INVALID, revision);
        }
        char[] chars = flags.toCharArray();
        chars[revision] = value;
        return new String(chars);
    }

    /** 数值列宽容收窄：int2/int4/int8 统一为 Long（JDBC 驱动按列型返回 Integer/Long；null 透传）。 */
    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long number) {
            return number;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
