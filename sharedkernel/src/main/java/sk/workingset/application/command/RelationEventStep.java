package sk.workingset.application.command;


import java.util.List;

import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.application.query.provider.RelationSourceQueryProvider;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * T7 创建/扩展索引后的关系事件 step。只发布事件，不写 Reference/Association 聚合。
 * 每个来源 Item 独立尝试；失败累计并交由任务标记，不自动重试。
 */
public final class RelationEventStep {
    private final TrackerRepository trackers;
    private final WorkingSetRepository workingSets;
    private final LightTrackerItemRepository items;
    private final RelationSourceQueryProvider source;
    private final RelationEventPublisher publisher;

    public RelationEventStep(TrackerRepository trackers, WorkingSetRepository workingSets,
                             LightTrackerItemRepository items, RelationSourceQueryProvider source,
                             RelationEventPublisher publisher) {
        this.trackers = trackers;
        this.workingSets = workingSets;
        this.items = items;
        this.source = source;
        this.publisher = publisher;
    }

    public StepResult publish(WorkingSetId target, TrackerId parent, OriginType originType, String originId) {
        try {
            Tracker child = trackers.findDerivedTracker(target, parent).orElse(null);
            sk.workingset.domain.WorkingSet targetSet = workingSets.findWorkingSetForCreation(target).orElse(null);
            if (child == null || targetSet == null || !targetSet.hasTrackerBinding(child.trackerId())) {
                return new StepResult(0, 0, true);
            }
            return publishIndexed(target, child, items.findInTracker(child.trackerId()), originType, originId);
        } catch (RuntimeException failure) {
            return new StepResult(0, 1, false);
        }
    }

    private StepResult publishIndexed(WorkingSetId target, Tracker child, List<LightTrackerItem> indexed,
                                      OriginType originType, String originId) {
        int published = 0;
        int failed = 0;
        for (LightTrackerItem item : indexed) {
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
