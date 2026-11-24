package com.airedgesoft.sharedkernel.businessframework.infrastructure.reference;

import com.airedgesoft.sharedkernel.businessframework.application.query.ReferenceEditorFacts;
import com.airedgesoft.sharedkernel.businessframework.application.query.ReferenceGuardQuery;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectRepository;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.Reference;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceGuardDomainService;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 引用守卫事实读端口实现（infrastructure 读侧；引用写用例 {@code ReferenceCommandService} 的装配方）。
 *
 * <p>
 * <b>只取事实不判定</b>（SKILL 6.5-20）：引用方编辑事实经 {@link ReferenceEditorFacts} 端口回注（源对象 BC 私有聚合，依赖倒置）； 既有有效行与目标 存在性走引用现表直查（历史求值在
 * {@link ReferenceRepositoryImpl}，口径 11）。
 */
@Component
public class ReferenceGuardQueryImpl implements ReferenceGuardQuery {

    private final ReferenceEditorFacts editorFacts;
    private final ReferenceRepository referenceRepository;
    private final LiteBusinessObjectRepository liteBusinessObjectRepository;

    public ReferenceGuardQueryImpl(ReferenceEditorFacts editorFacts, ReferenceRepository referenceRepository,
            LiteBusinessObjectRepository liteBusinessObjectRepository) {
        this.editorFacts = Objects.requireNonNull(editorFacts, "editorFacts");
        this.referenceRepository = Objects.requireNonNull(referenceRepository, "referenceRepository");
        this.liteBusinessObjectRepository = Objects.requireNonNull(liteBusinessObjectRepository,
                "liteBusinessObjectRepository");
    }

    @Override
    public ReferenceGuardDomainService.EditorFacts loadEditorFacts(Long sourceId) {
        return editorFacts.loadEditorFacts(sourceId);
    }

    @Override
    public List<Reference> loadEffectiveRows(Long sourceId, String attributeApiId, int revision) {
        return referenceRepository.findEffectiveRows(sourceId, attributeApiId, revision);
    }

    @Override
    public List<Long> loadExistingTargetIds(List<Long> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        List<LiteBusinessObject> briefs = liteBusinessObjectRepository.findByIds(targetIds);
        List<Long> existing = new ArrayList<>(briefs.size());
        for (LiteBusinessObject brief : briefs) {
            if (!brief.isDeleted()) {
                existing.add(brief.id());
            }
        }
        return existing;
    }
}
