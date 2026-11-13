package sk.workingset.application.event;

/** A durable creation request has been accepted. */
public record WorkingSetCreationSubmitted(String creationId) {}
