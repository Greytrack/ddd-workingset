package com.airedgesoft.sharedkernel.businessframework.domain.reference;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 领域服务（纯判定 · 断言式 · 无 IO）：引用编辑守卫（引用稿 §四、类图 11 号稿）。
 *
 * <p>
 * 属性规则、目标存在性、本版次有效行等事实由应用层经 {@code ReferenceGuardQuery} 预取成 Facts 传入（SKILL 6.5-20：领域侧不注入 Query／仓储）。判定失败抛域异常，整批回滚。
 *
 * <p>
 * <b>三条硬守卫</b>：①必须先签出（口径 15）；②单次 ≤200 行（口径 14）；③<b>判重</b>（裁决 5，推翻口径 12—— 同一属性下目标＋目标版次重复、提交集合内部重复均拒绝）。
 */
public class ReferenceGuardDomainService {

    /** 判定所需事实包：引用方版次与签出态由应用层预取。 */
    public record EditorFacts(Long sourceId, int sourceRevision, boolean checkedOut, boolean deleted) {

        public EditorFacts {
            Objects.requireNonNull(sourceId, "sourceId");
        }
    }

    /** 判定所需事实包：既有有效行（本属性本版次）由应用层预取。 */
    public record ExistingRowsFacts(List<Reference> effectiveRows) {

        public ExistingRowsFacts {
            effectiveRows = effectiveRows == null ? List.of() : List.copyOf(effectiveRows);
        }
    }

    /** 提交单元：目标 ＋ 目标版次（修改引用属性的整值替换与新增共用）。 */
    public record TargetInput(Long targetId, TargetRevision targetRevision) {

        public TargetInput {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(targetRevision, "targetRevision");
        }
    }

    /**
     * 新增引用守卫：已签出、未删除、单次 ≤200、判重（含集合内部重复）。
     *
     * @param sourceId
     *            引用方需求 id
     * @param targets
     *            提交目标集合
     * @param editor
     *            引用方版次/签出态事实
     * @param existing
     *            既有有效行事实
     */
    public void assertAddable(Long sourceId, List<TargetInput> targets, EditorFacts editor,
            ExistingRowsFacts existing) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(targets, "targets");
        assertEditable(editor);
        assertBatchLimit(targets.size());
        assertNoDuplicate(sourceId, targets, existing);
    }

    /**
     * 整值替换守卫（修改引用属性）：同新增；不判「与被替换行」之外的差集——替换语义由应用服务编排（失效／丢弃＋新增）。
     *
     * @param sourceId
     *            引用方需求 id
     * @param attributeApiId
     *            引用属性 apiId
     * @param targets
     *            提交目标集合（新值全量）
     * @param editor
     *            引用方版次/签出态事实
     * @param existing
     *            既有有效行事实（替换前的本属性有效行，判重依据）
     */
    public void assertReplaceable(Long sourceId, String attributeApiId, List<TargetInput> targets, EditorFacts editor,
            ExistingRowsFacts existing) {
        Objects.requireNonNull(attributeApiId, "attributeApiId");
        // 替换的判重按「新值集合内 + 新值 vs 未被替换的其他属性行」——本轮判重口径：新值集合内部 + 新值 vs 既有有效行（替换后旧行全部失效/丢弃，故以既有行集为判重池）。
        assertAddable(sourceId, targets, editor, existing);
    }

    /**
     * 删除守卫：已签出、行存在且归属本版次（行事实由应用层预取）。
     *
     * @param referenceIds
     *            待删引用 id
     * @param editor
     *            引用方版次/签出态事实
     */
    public void assertRemovable(List<Long> referenceIds, EditorFacts editor) {
        assertEditable(editor);
        assertBatchLimit(referenceIds == null ? 0 : referenceIds.size());
    }

    private void assertEditable(EditorFacts editor) {
        if (editor.deleted()) {
            throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.REFERENCE_SOURCE_DELETED,
                    editor.sourceId());
        }
        if (!editor.checkedOut()) {
            throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.REFERENCE_REQUIRES_CHECKOUT,
                    editor.sourceId(), editor.sourceRevision());
        }
    }

    private void assertBatchLimit(int size) {
        if (size > Reference.BATCH_LIMIT) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.REFERENCE_BATCH_LIMIT_EXCEEDED, size);
        }
    }

    /** 判重（裁决 5）：提交集合内部重复，或与既有有效行重复（键＝targetId——同一引用属性下同一目标只挂一行，目标版次形态作为键的澄清部分：同目标不同版次形态仍算撞既有行）。 */
    private void assertNoDuplicate(Long sourceId, List<TargetInput> targets, ExistingRowsFacts existing) {
        Set<String> seen = new HashSet<>();
        for (TargetInput target : targets) {
            if (!seen.add(dedupeKey(target.targetId(), target.targetRevision()))) {
                throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                        SharedKernelResultCode.REFERENCE_INPUT_DUPLICATED, sourceId);
            }
        }
        Set<String> existingKeys = new HashSet<>();
        for (Reference row : existing.effectiveRows()) {
            existingKeys.add(dedupeKey(row.targetId(), row.targetRevision()));
        }
        for (TargetInput target : targets) {
            if (existingKeys.contains(dedupeKey(target.targetId(), target.targetRevision()))) {
                throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.REFERENCE_DUPLICATED,
                        sourceId, target.targetId(),
                        target.targetRevision().revision() == null ? "最新" : "R" + target.targetRevision().revision());
            }
        }
    }

    private String dedupeKey(Long targetId, TargetRevision targetRevision) {
        // 判重键＝targetId：同一引用属性下，同一目标无论「跟随最新」还是「钉住某版次」都视为同一引用目标——
        // 重复提交同一目标拒绝（裁决 5「目标＋目标版次重复」的业务含义即「同一目标不重复挂出」）。
        return String.valueOf(targetId);
    }
}
