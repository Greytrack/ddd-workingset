package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider.AssociationFact;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.RelationSourceQueryProvider.ReferenceFact;
import com.airedgesoft.sharedkernel.workingset.domain.event.AssociationCreationRequestedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.DanglingReferenceRepairRequestedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.ReferenceCreationRequestedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.RelationEventMetadataEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发布关系请求事件。WorkingSet 不写 Reference/Association 聚合；事件由关系服务消费。
 *
 * <p>每个事件独立生成 eventId；{@code originalRelationId} 保留业务关系聚合身份，供消费方回查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class RelationEventPublisher {
    private final ApplicationEventPublisher events;
    

    /**
     * 发布 Reference 创建请求事件，返回已发布事件列表。
     */
    public List<ReferenceCreationRequestedEvent> publishReferenceCreations(String originId, OriginType originType,
                                                                           List<ReferenceFact> facts) {
        return facts.stream().map(fact -> new ReferenceCreationRequestedEvent(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    /**
     * 发布悬空 Reference 修复请求事件，返回已发布事件列表。
     */
    public List<DanglingReferenceRepairRequestedEvent> publishReferenceRepairs(String originId, OriginType originType,
                                                                               List<ReferenceFact> facts) {
        return facts.stream().map(fact -> new DanglingReferenceRepairRequestedEvent(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    /**
     * 发布 Association 创建请求事件，返回已发布事件列表。
     */
    public List<AssociationCreationRequestedEvent> publishAssociationCreations(String originId, OriginType originType,
                                                                               List<AssociationFact> facts) {
        return facts.stream().map(fact -> new AssociationCreationRequestedEvent(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    private RelationEventMetadataEvent metadata(String originalRelationId, String originId, OriginType originType) {
        return new RelationEventMetadataEvent(Long.toString(Ids.nextId()), originalRelationId, originId, originType,
                System.currentTimeMillis());
    }
}
