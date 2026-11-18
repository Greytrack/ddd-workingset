package sk.workingset.domain.event;

/** A durable creation request has been accepted. */
public record WorkingSetCreationSubmitted(String creationId) {}
