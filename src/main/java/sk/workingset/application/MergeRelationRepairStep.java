package sk.workingset.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sk.workingset.application.event.ReferenceEndpoint;
import sk.workingset.application.event.RelationEventMetadata.OriginType;
import sk.workingset.application.port.RelationSourcePort;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeRound.Entry;
import sk.workingset.domain.merge.MergeRound.ItemResult;

/**
 * Merge 完成后的关系修复 step。只对本轮实际成功且改变目标变体的条目发布
 * {@code DanglingReferenceRepairRequested}；失败只记录，不改变 Merge 终态。
 */
public final class MergeRelationRepairStep {
    private static final Logger log = LoggerFactory.getLogger(MergeRelationRepairStep.class);

    private final RelationSourcePort source;
    private final RelationEventPublisher publisher;

    public MergeRelationRepairStep(RelationSourcePort source, RelationEventPublisher publisher) {
        this.source = source;
        this.publisher = publisher;
    }

    public StepResult repair(MergeRound round) {
        int published = 0;
        int failed = 0;
        for (ReferenceEndpoint target : changedTargets(round).values()) {
            try {
                published += publisher.publishReferenceRepairs(round.id(), OriginType.MERGE,
                        source.referencesTargeting(target)).size();
            } catch (RuntimeException failure) {
                failed++;
                log.warn("Merge {} relation repair could not be published for {}", round.id(),
                        target.localId(), failure);
            }
        }
        return new StepResult(published, failed);
    }

    Map<String, ReferenceEndpoint> changedTargets(MergeRound round) {
        Map<String, ReferenceEndpoint> targets = new LinkedHashMap<>();
        for (Entry entry : round.entries()) {
            ItemResult result = entry.result();
            if (result == null || !result.succeeded() || !changesTarget(result.operation())) {
                continue;
            }
            LocalId localId = result.businessRef() == null
                    ? entry.evidence().target().localId()
                    : LocalId.of(result.businessRef());
            if (localId == null) {
                continue;
            }
            var target = new ReferenceEndpoint(round.target(), localId, entry.evidence().family(),
                    entry.evidence().trackers().target(), entry.evidence().target().revision());
            targets.put(localId.value(), target);
        }
        return targets;
    }

    private static boolean changesTarget(String operation) {
        return operation != null && !"KEEP_TARGET".equals(operation) && !"DEFER".equals(operation);
    }

    public record StepResult(int published, int failed) {
        public boolean succeeded() {
            return failed == 0;
        }
    }
}
