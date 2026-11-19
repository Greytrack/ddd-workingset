package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import sk.workingset.application.query.provider.RelationSourceQueryProvider.AssociationFact;
import sk.workingset.application.query.provider.RelationSourceQueryProvider.ReferenceFact;
import sk.workingset.domain.event.AssociationCreationRequestedEvent;
import sk.workingset.domain.event.DanglingReferenceRepairRequestedEvent;
import sk.workingset.domain.event.ReferenceCreationRequestedEvent;
import sk.workingset.domain.event.RelationEventMetadataEvent;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;

import java.time.Instant;
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
    private final IdentifierGenerator ids;


    public List<ReferenceCreationRequestedEvent> publishReferenceCreations(String originId, OriginType originType,
                                                                           List<ReferenceFact> facts) {
        return facts.stream().map(fact -> new ReferenceCreationRequestedEvent(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    public List<DanglingReferenceRepairRequestedEvent> publishReferenceRepairs(String originId, OriginType originType,
                                                                               List<ReferenceFact> facts) {
        return facts.stream().map(fact -> new DanglingReferenceRepairRequestedEvent(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    public List<AssociationCreationRequestedEvent> publishAssociationCreations(String originId, OriginType originType,
                                                                               List<AssociationFact> facts) {
        return facts.stream().map(fact -> new AssociationCreationRequestedEvent(
                        metadata(fact.originalRelationId(), originId, originType), fact.source(), fact.target()))
                .peek(events::publishEvent).toList();
    }

    private RelationEventMetadataEvent metadata(String originalRelationId, String originId, OriginType originType) {
        return new RelationEventMetadataEvent(Long.toString(ids.nextId(null).longValue()), originalRelationId, originId, originType,
                Instant.now());
    }
}
