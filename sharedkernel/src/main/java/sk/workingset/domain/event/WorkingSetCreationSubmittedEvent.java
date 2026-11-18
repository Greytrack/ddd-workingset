package sk.workingset.domain.event;

/** A durable creation request has been accepted. */
public record WorkingSetCreationSubmittedEvent(String creationId) {}
