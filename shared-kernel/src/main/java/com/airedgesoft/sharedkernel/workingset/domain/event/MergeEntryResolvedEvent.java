package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * ALM 对某个 Merge 条目的最终处理结果；SK 据此记录条目结果并收敛轮次。
 */
public record MergeEntryResolvedEvent(String roundId,
                                      String entryId,
                                      Outcome outcome,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Long businessRef,
                                      Integer sourceRevision,
                                      Integer targetRevision,
                                      boolean created,
                                      boolean inherited,
                                      String failure) {

    public MergeEntryResolvedEvent {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(targetRevision, "targetRevision");
    }

    public enum Outcome {APPLIED, CONFLICT, REJECTED, STALE_EVIDENCE, UNKNOWN}
}
