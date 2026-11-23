package com.airedgesoft.sharedkernel.workingset.application.query.provider;

import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;

import java.util.Optional;

/** Reads durable target requests from the event publication registry. */
public interface CowRequestPublications {
    boolean exists(Long sourceLocalId, Long targetLocalId);

    Optional<TrackerItemMaterializationRequestedEvent> find(long eventId, Long targetLocalId);
}
