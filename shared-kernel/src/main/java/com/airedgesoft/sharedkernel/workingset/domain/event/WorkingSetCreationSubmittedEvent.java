package com.airedgesoft.sharedkernel.workingset.domain.event;

/**
 * A durable creation request has been accepted.
 */
public record WorkingSetCreationSubmittedEvent(String creationId) {
}
