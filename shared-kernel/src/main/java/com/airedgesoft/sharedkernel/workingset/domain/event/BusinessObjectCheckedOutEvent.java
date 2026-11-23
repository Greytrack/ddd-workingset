package com.airedgesoft.sharedkernel.workingset.domain.event;

/**
 * A business BC committed checkout and created a new revision.
 */
public record BusinessObjectCheckedOutEvent(Long localId,
                                       int beforeRevision, int afterRevision, String effectiveFlags) {
    public BusinessObjectCheckedOutEvent(Long localId, int beforeRevision, int afterRevision) {
        this(localId, beforeRevision, afterRevision, "0010000000");
    }
}
