package com.airedgesoft.sharekernel.workingset.domain.event;

/**
 * The business BC committed check-in at the current revision and supplies its ten effective flags.
 */
public record BusinessObjectCheckedInEvent(Long localId,
                                           int revision, String effectiveFlags) {
}
