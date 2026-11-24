package com.airedgesoft.sharedkernel.businessframework.infrastructure.association;

import com.airedgesoft.sharedkernel.businessframework.application.query.AssociationGuardQuery;
import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;
import com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationGuardDomainService;
import com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationRepository;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 关联守卫事实读端口实现（infrastructure 读侧；关联写用例的装配方）。
 *
 * <p>
 * <b>只取事实不判定</b>：端对象事实走 lite 业务对象快照（存在/删除位/分类），既有行走关联现表查询。
 */
@Component
public class AssociationGuardQueryImpl implements AssociationGuardQuery {

    private final LiteBusinessObjectRepository liteBusinessObjectRepository;
    private final AssociationRepository associationRepository;

    public AssociationGuardQueryImpl(LiteBusinessObjectRepository liteBusinessObjectRepository,
            AssociationRepository associationRepository) {
        this.liteBusinessObjectRepository = Objects.requireNonNull(liteBusinessObjectRepository,
                "liteBusinessObjectRepository");
        this.associationRepository = Objects.requireNonNull(associationRepository, "associationRepository");
    }

    @Override
    public AssociationGuardDomainService.EndFacts loadEndFacts(Long objectId) {
        if (objectId == null) {
            return new AssociationGuardDomainService.EndFacts(0L, null, false, false);
        }
        return liteBusinessObjectRepository.findById(objectId).map(this::toFacts)
                .orElseGet(() -> new AssociationGuardDomainService.EndFacts(objectId, null, false, false));
    }

    @Override
    public Map<Long, AssociationGuardDomainService.EndFacts> loadEndFactsBatch(Collection<Long> objectIds) {
        if (objectIds == null || objectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AssociationGuardDomainService.EndFacts> facts = new HashMap<>();
        liteBusinessObjectRepository.findByIds(objectIds).forEach(brief -> facts.put(brief.id(), toFacts(brief)));
        objectIds.forEach(
                id -> facts.putIfAbsent(id, new AssociationGuardDomainService.EndFacts(id, null, false, false)));
        return facts;
    }

    @Override
    public List<Association> loadDownstreamRows(Long sourceId, Collection<Long> targetIds) {
        if (sourceId == null || targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }
        return associationRepository.findDownstreamRows(sourceId).stream()
                .filter(row -> targetIds.contains(row.targetId())).toList();
    }

    private AssociationGuardDomainService.EndFacts toFacts(LiteBusinessObject brief) {
        return new AssociationGuardDomainService.EndFacts(brief.id(), brief.type(), true, brief.isDeleted());
    }
}
