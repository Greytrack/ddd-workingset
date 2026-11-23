package com.airedgesoft.sharekernel.workingset.domain.event;

/** Local business content was committed for a source-backed variant. */
public record BusinessObjectLocalizedEvent(Long localId, int revision) {
}
