package com.airedgesoft.alm.requirement.application.port;

/** Transaction-scoped coordination and lookup of durable results for ALM merge entries. */
public interface RequirementMergeResultPublications {
    void lockEntry(String roundId, String entryId);

    boolean exists(String roundId, String entryId);
}
