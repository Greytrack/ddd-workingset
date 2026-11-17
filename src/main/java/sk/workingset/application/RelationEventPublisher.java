package sk.workingset.application;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;

import sk.workingset.application.event.AssociationCreationRequested;
import sk.workingset.application.event.DanglingReferenceRepairRequested;
import sk.workingset.application.event.ReferenceCreationRequested;
import sk.workingset.application.event.RelationEventMetadata;
import sk.workingset.application.event.RelationEventMetadata.OriginType;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.RelationSourcePort.AssociationFact;
import sk.workingset.application.port.RelationSourcePort.ReferenceFact;

/**
 * 发布关系请求事件。WorkingSet 不写 Reference/Association 聚合；事件由关系服务消费。
 *
 * <p>每个事件独立生成 eventId；{@code originalRelationId} 保留业务关系聚合身份，供消费方回查。
 */
public final class RelationEventPublisher {
    private final ApplicationEventPublisher events;
    private final IdentifierGenerator ids;

    public RelationEventPublisher(ApplicationEventPublisher events, IdentifierGenerator ids) {
        this.events = events;
        this.ids = ids;
    }

    public List<ReferenceCreationRequested> publishReferenceCreations(String originId, OriginType originType,
                                                                     List<ReferenceFact> facts) {
        return facts.stream().map(fact -> new ReferenceCreationRequested(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    public List<DanglingReferenceRepairRequested> publishReferenceRepairs(String originId, OriginType originType,
                                                                           List<ReferenceFact> facts) {
        return facts.stream().map(fact -> new DanglingReferenceRepairRequested(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    public List<AssociationCreationRequested> publishAssociationCreations(String originId, OriginType originType,
                                                                          List<AssociationFact> facts) {
        return facts.stream().map(fact -> new AssociationCreationRequested(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    private RelationEventMetadata metadata(String originalRelationId, String originId, OriginType originType) {
        return new RelationEventMetadata(Long.toString(ids.nextId()), originalRelationId, originId, originType,
                Instant.now());
    }
}
