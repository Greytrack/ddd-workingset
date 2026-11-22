package com.airedgesoft.alm.requirement.adapters.workingset;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.application.query.provider.RequirementQueryProvider;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.application.FailureReason;
import com.airedgesoft.sharekernel.workingset.application.TrackerItemContent;
import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerItemQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;

import java.util.Map;
import java.util.Objects;

/**
 * ALM 查询侧 Adapter：用 Requirement 读模型实现 SK 的只读内容端口。
 *
 * <p>不使用 Requirement 写仓储，避免 Merge 比较等查询流程触达命令侧聚合。
 */
public class RequirementReadModelAdapter implements TrackerItemQueryProvider {

    private final RequirementQueryProvider query;
    private final LocalId localId;

    public RequirementReadModelAdapter(RequirementQueryProvider query, LocalId localId) {
        this.query = Objects.requireNonNull(query, "query");
        this.localId = Objects.requireNonNull(localId, "localId");
    }

    private static DomainException notFound(LocalId localId) {
        return DomainException.withMessage(FailureReason.NOT_FOUND,
                "requirement not found: " + localId.value());
    }

    @Override
    public TrackerItemContent readAt(Revision revision) {
        return content(query.find(localId.numericValue())
                .orElseThrow(() -> notFound(localId)), revision);
    }

    @Override
    public TrackerItemContent readInheritedAt(Revision revision) {
        return readAt(revision);
    }

    @Override
    public ContentEvidence evidence() {
        RequirementDetail detail = find();
        return new ContentEvidence(CommonId.of(detail.commonId()), Revision.of(detail.revision()),
                detail.version(), detail.lockVersion(), detail.deleted());
    }

    private TrackerItemContent content(RequirementDetail detail, Revision revision) {
        if (detail.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, "requirement deleted: " + localId.value());
        }
        if (detail.revision() != revision.value()) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE,
                    "required revision is unavailable: " + revision.value());
        }
        return new TrackerItemContent(revision, Map.of(
                "title", detail.title(),
                "description", detail.description()));
    }

    private RequirementDetail find() {
        return query.find(localId.numericValue()).orElseThrow(() -> notFound(localId));
    }
}
