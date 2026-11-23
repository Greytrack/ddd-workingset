package com.airedgesoft.sharekernel.workingset.domain.event;

/**
 * A business BC committed checkout and created a new revision.
 */
public record BusinessObjectCheckedOutEvent(Long localId,
                                       int beforeRevision, int afterRevision) {
}
