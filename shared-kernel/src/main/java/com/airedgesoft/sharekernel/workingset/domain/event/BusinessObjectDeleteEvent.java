package com.airedgesoft.sharekernel.workingset.domain.event;

import com.airedgesoft.sharekernel.workingset.domain.LocalId;

/**
 * The business object committed a soft deletion or restoration.
 */
public record BusinessObjectDeleteEvent(LocalId localId, boolean deleted) {
}
