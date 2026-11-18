package sk.workingset.application.command;


import java.time.Instant;
import java.util.List;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.ApplicationEventPublisher;

import sk.workingset.domain.event.AssociationCreationRequested;
import sk.workingset.domain.event.DanglingReferenceRepairRequested;
import sk.workingset.domain.event.ReferenceCreationRequested;
import sk.workingset.domain.event.RelationEventMetadata;
import sk.workingset.domain.event.RelationEventMetadata.OriginType;
import sk.workingset.application.query.provider.RelationSourcePort.AssociationFact;
import sk.workingset.application.query.provider.RelationSourcePort.ReferenceFact;

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
        return new RelationEventMetadata(Long.toString(ids.nextId(null).longValue()), originalRelationId, originId, originType,
                Instant.now());
    }
}
