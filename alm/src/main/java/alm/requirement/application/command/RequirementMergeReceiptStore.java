package alm.requirement.application.command;

import sk.workingset.domain.event.MergeEntryResolvedEvent;

import java.util.Optional;

/**
 * ALM 对 Merge 条目的幂等结果记录。
 */
public interface RequirementMergeReceiptStore {
    Optional<MergeEntryResolvedEvent> find(String roundId, String entryId);

    void save(MergeEntryResolvedEvent result);
}
