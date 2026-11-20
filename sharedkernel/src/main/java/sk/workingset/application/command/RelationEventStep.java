package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.application.query.provider.LightTrackerItemSummary;
import sk.workingset.application.query.provider.RelationSourceQueryProvider;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;

import java.util.List;

/**
 * T7 创建/扩展索引后的关系事件 step。只发布事件，不写 Reference/Association 聚合。
 * 每个来源 Item 独立尝试；失败累计并交由任务标记，不自动重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class RelationEventStep {
    private final TrackerRepository trackers;
    private final WorkingSetRepository workingSets;
    private final LightTrackerItemQueryProvider itemQueries;
    private final RelationSourceQueryProvider source;
    private final RelationEventPublisher publisher;


    /**
     * 对已索引的派生 Tracker 发布关系创建/修复事件；失败累计，不自动重试。
     */
    public StepResult publish(WorkingSetId target, TrackerId parent, OriginType originType, String originId) {
        try {
            Tracker child = trackers.findDerivedTracker(target, parent).orElse(null);
            WorkingSet targetSet = workingSets.findByIdForCreation(target).orElse(null);
            if (child == null || targetSet == null || !targetSet.hasTrackerBinding(child.trackerId())) {
                return new StepResult(0, 0, true);
            }
            return publishIndexed(target, child, itemQueries.findInTracker(child.trackerId()), originType, originId);
        } catch (RuntimeException failure) {
            return new StepResult(0, 1, false);
        }
    }

    private StepResult publishIndexed(WorkingSetId target, Tracker child, List<LightTrackerItemSummary> indexed,
                                      OriginType originType, String originId) {
        int published = 0;
        int failed = 0;
        for (LightTrackerItemSummary item : indexed) {
            try {
                sk.workingset.domain.event.ReferenceEndpointEvent referenceSource = new sk.workingset.domain.event.ReferenceEndpointEvent(target,
                        item.localId(), item.commonId(), child.trackerId(), item.revision());
                sk.workingset.domain.event.AssociationEndpointEvent associationSource = new sk.workingset.domain.event.AssociationEndpointEvent(target,
                        item.localId(), item.commonId(), child.trackerId());
                published += publisher.publishReferenceCreations(originId, originType,
                        source.upstreamReferences(referenceSource)).size();
                published += publisher.publishAssociationCreations(originId, originType,
                        source.upstreamAssociations(associationSource)).size();
                published += publisher.publishReferenceRepairs(originId, originType,
                        source.referencesTargeting(referenceSource)).size();
            } catch (RuntimeException failure) {
                failed++;
            }
        }
        return new StepResult(published, failed, false);
    }

    public record StepResult(int published, int failed, boolean skipped) {
        public boolean succeeded() {
            return failed == 0;
        }
    }
}
