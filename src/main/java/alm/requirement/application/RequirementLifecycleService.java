package alm.requirement.application;

import org.springframework.transaction.annotation.Transactional;

import alm.requirement.infrastructure.JdbcRequirementRepository;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.domain.LocalId;

/** 协调永久删除与仍依赖该内容的工作集变体。 */
public class RequirementLifecycleService {
    private final JdbcRequirementRepository requirements;
    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;

    public RequirementLifecycleService(JdbcRequirementRepository requirements, TrackerRepository trackers,
                                       LightTrackerItemRepository items) {
        this.requirements = requirements;
        this.trackers = trackers;
        this.items = items;
    }

    @Transactional
    public void delete2(LocalId id) {
        var candidate = requirements.findById(id).orElseThrow(() -> missing(id));
        trackers.lockTracker(candidate.trackerId());
        var current = requirements.lockById(id).orElseThrow(() -> missing(id));
        if (items.hasSourceBackedDependents(id)
                || items.findById(id).filter(item -> !item.materialized()).isPresent()) {
            throw new ApplicationFailure(FailureReason.PREPARING, "Requirement content is still needed by COW");
        }
        items.remove(id);
        requirements.purge(current);
    }

    private static ApplicationFailure missing(LocalId id) {
        return new ApplicationFailure(FailureReason.NOT_FOUND, id.value());
    }
}
