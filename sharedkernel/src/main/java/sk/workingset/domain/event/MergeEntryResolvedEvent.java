package sk.workingset.domain.event;

import java.util.Objects;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/** ALM 对某个 Merge 条目的最终处理结果；SK 据此记录条目结果并收敛轮次。 */
public record MergeEntryResolvedEvent(String roundId,
                                      String entryId,
                                      Outcome outcome,
                                      LocalId businessRef,
                                      Revision sourceRevision,
                                      Revision targetRevision,
                                      boolean created,
                                      boolean inherited,
                                      String failure) {

    public enum Outcome { APPLIED, CONFLICT, REJECTED, STALE_EVIDENCE, UNKNOWN }

    public MergeEntryResolvedEvent {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(targetRevision, "targetRevision");
    }
}
