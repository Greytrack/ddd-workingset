package sk.workingset.domain.event;

import sk.workingset.domain.LocalId;

/**
 * The business object committed a soft deletion or restoration.
 */
public record BusinessObjectDeleteEvent(LocalId localId, boolean deleted) {
}
