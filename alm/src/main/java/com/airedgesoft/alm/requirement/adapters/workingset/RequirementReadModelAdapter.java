package com.airedgesoft.alm.requirement.adapters.workingset;


import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.application.query.provider.RequirementQueryProvider;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.TrackerItemContent;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerItemQueryProvider;

import java.util.Map;
import java.util.Objects;

/**
 * ALM 查询侧 Adapter：用 Requirement 读模型实现 SK 的只读内容端口。
 *
 * <p>不使用 Requirement 写仓储，避免 Merge 比较等查询流程触达命令侧聚合。
 */
public class RequirementReadModelAdapter implements TrackerItemQueryProvider {

    private final RequirementQueryProvider query;
    private final Long localId;

    public RequirementReadModelAdapter(RequirementQueryProvider query, Long localId) {
        this.query = Objects.requireNonNull(query, "query");
        this.localId = Objects.requireNonNull(localId, "localId");
    }

    private static DomainException notFound(Long localId) {
        return DomainException.withMessage(FailureReason.NOT_FOUND,
                "requirement not found: " + Long.toString(localId));
    }

    @Override
    public TrackerItemContent readAt(Integer revision) {
        RequirementDetail current = find();
        if (current.deleted() || current.revision() == revision) {
            return content(current, revision);
        }
        return content(query.findRevision(localId, revision)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE,
                        "required revision is unavailable: " + revision)), revision);
    }

    @Override
    public TrackerItemContent readInheritedAt(Integer revision) {
        return readAt(revision);
    }

    @Override
    public ContentEvidence evidence() {
        RequirementDetail detail = find();
        return new ContentEvidence(detail.commonId(), detail.revision(),
                detail.version(), detail.lockVersion(), detail.deleted());
    }

    private TrackerItemContent content(RequirementDetail detail, Integer revision) {
        if (detail.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, "requirement deleted: " + Long.toString(localId));
        }
        if (detail.revision() != revision) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE,
                    "required revision is unavailable: " + revision);
        }
        return new TrackerItemContent(revision, Map.of(
                "title", detail.title(),
                "description", detail.description()));
    }

    private RequirementDetail find() {
        return query.find(localId).orElseThrow(() -> notFound(localId));
    }
}
