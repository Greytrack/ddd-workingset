package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sk.workingset.application.query.provider.RelationSourceQueryProvider;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.event.ReferenceEndpointEvent;
import sk.workingset.domain.event.RelationEventMetadataEvent.OriginType;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeRound.Entry;
import sk.workingset.domain.merge.MergeRound.ItemResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merge 完成后的关系修复 step。只对本轮实际成功且改变目标变体的条目发布
 * {@code DanglingReferenceRepairRequestedEvent}；失败只记录，不改变 Merge 终态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class MergeRelationRepairStep {

    private final RelationSourceQueryProvider source;
    private final RelationEventPublisher publisher;

    private static boolean changesTarget(String operation) {
        return operation != null && !"IGNORED".equals(operation);
    }

    /**
     * 对本轮实际成功且改变目标的条目发布悬空引用修复事件；失败只记录，不改变 Merge 终态。
     */
    public StepResult repair(MergeRound round) {
        int published = 0;
        int failed = 0;
        for (ReferenceEndpointEvent target : changedTargets(round).values()) {
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

    /**
     * 汇总本轮成功且改变目标变体的条目端点（按 LocalId 去重）。
     */
    public Map<String, ReferenceEndpointEvent> changedTargets(MergeRound round) {
        Map<String, ReferenceEndpointEvent> targets = new LinkedHashMap<>();
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
            ReferenceEndpointEvent target = new ReferenceEndpointEvent(round.target(), localId, entry.evidence().family(),
                    entry.evidence().trackers().target(), entry.evidence().target().revision());
            targets.put(localId.value(), target);
        }
        return targets;
    }

    public record StepResult(int published, int failed) {
        public boolean succeeded() {
            return failed == 0;
        }
    }
}
