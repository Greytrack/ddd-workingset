package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 领域服务（纯判定 · 断言式 · 无 IO）：关联行守卫（关联稿类图 §二）。
 *
 * <p>
 * 事实由应用层经 {@code AssociationGuardQuery} 预取成 Facts 传入（领域侧不注入 Query／仓储）。判定失败抛域异常，整批回滚。
 *
 * <p>
 * <b>与设计稿的偏差</b>：守卫入参采用领域级事实/草稿（{@code Draft} / {@code EndFacts}），不直接吃应用层 Command—— 保持领域层不反向依赖应用层（对齐
 * {@code ReferenceGuardDomainService} 既有口径）。
 *
 * <p>
 * <b>自关联与重复目标不在守卫内拒绝</b>：用户裁定 5 按 v3 静默剔除，过滤由应用服务在创建编排中完成（守卫只判端存在性与归属）。
 */
public class AssociationGuardDomainService {

    /** 端对象事实（应用层预取：存在 / 删除位 / 分类）。 */
    public record EndFacts(Long objectId, Long objectType, boolean exists, boolean deleted) {

        public EndFacts {
            Objects.requireNonNull(objectId, "objectId");
        }
    }

    /** 创建草稿（一条目标；源与类型由应用服务装配）。 */
    public record Draft(Long sourceId, Long sourceType, Long targetId, Long targetType) {

        public Draft {
            Objects.requireNonNull(sourceId, "sourceId");
            Objects.requireNonNull(sourceType, "sourceType");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(targetType, "targetType");
        }
    }

    /** 创建事实：源端事实 + 目标端事实（按目标 id 索引）。 */
    public record CreateFacts(EndFacts source, Map<Long, EndFacts> targets) {

        public CreateFacts {
            Objects.requireNonNull(source, "source");
            targets = targets == null ? Map.of() : Map.copyOf(targets);
        }
    }

    /**
     * 新增守卫：源/目标存在、未删、分类一致（自关联与重复由应用层静默剔除）。
     *
     * @param drafts
     *            创建草稿集合
     * @param facts
     *            端对象事实
     */
    public void assertCreatable(List<Draft> drafts, CreateFacts facts) {
        Objects.requireNonNull(drafts, "drafts");
        Objects.requireNonNull(facts, "facts");
        assertSource(facts.source(), facts.source().objectId());
        for (Draft draft : drafts) {
            if (!facts.source().objectId().equals(draft.sourceId())) {
                throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                        SharedKernelResultCode.ASSOCIATION_ENDPOINTS_REQUIRED, draft.sourceId(), draft.targetId());
            }
            validateType(facts.source(), draft.sourceType());
            EndFacts target = facts.targets().get(draft.targetId());
            assertTarget(target, draft.targetId());
            validateType(target, draft.targetType());
        }
    }

    /**
     * 修改/删除守卫：行必须存在且归属该源对象（请求行集合由应用层预取）。
     *
     * @param sourceId
     *            源对象 id
     * @param rows
     *            已加载的行
     * @param requestedRowIds
     *            请求的行 id 集合
     */
    public void assertRowsOwnedBy(Long sourceId, List<Association> rows, Collection<Long> requestedRowIds) {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(rows, "rows");
        List<Long> requested = requestedRowIds == null ? List.of() : List.copyOf(requestedRowIds);
        Set<Long> loaded = rows.stream().map(Association::associationId).collect(Collectors.toSet());
        if (!loaded.containsAll(requested)) {
            throw new DomainException(DomainErrorType.NOT_FOUND, SharedKernelResultCode.ASSOCIATION_ROW_NOT_FOUND,
                    requested);
        }
        for (Association row : rows) {
            if (!sourceId.equals(row.sourceId())) {
                throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.ASSOCIATION_ROW_NOT_OWNED,
                        row.associationId(), sourceId);
            }
        }
    }

    private void assertSource(EndFacts end, Long expectedObjectId) {
        assertPresent(end, expectedObjectId);
        if (end.deleted()) {
            throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.ASSOCIATION_SOURCE_DELETED,
                    expectedObjectId);
        }
    }

    private void assertTarget(EndFacts end, Long expectedObjectId) {
        assertPresent(end, expectedObjectId);
        if (end.deleted()) {
            throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.ASSOCIATION_TARGET_DELETED,
                    expectedObjectId);
        }
    }

    private void assertPresent(EndFacts end, Long expectedObjectId) {
        if (end == null || !end.exists()) {
            throw new DomainException(DomainErrorType.NOT_FOUND, SharedKernelResultCode.ASSOCIATION_END_NOT_FOUND,
                    expectedObjectId);
        }
    }

    private void validateType(EndFacts end, Long declaredType) {
        if (end.objectType() != null && !end.objectType().equals(declaredType)) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.ASSOCIATION_END_TYPE_MISMATCH, end.objectId(), declaredType,
                    end.objectType());
        }
    }
}
