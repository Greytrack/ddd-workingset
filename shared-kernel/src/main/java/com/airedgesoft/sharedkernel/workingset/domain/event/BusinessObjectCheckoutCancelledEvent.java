package com.airedgesoft.sharedkernel.workingset.domain.event;

/**
 * A business BC committed checkout cancellation. effectiveFlags describes the restored revision.
 */
public record BusinessObjectCheckoutCancelledEvent(Long localId,
                                       int beforeRevision, int afterRevision, String effectiveFlags) {
}
