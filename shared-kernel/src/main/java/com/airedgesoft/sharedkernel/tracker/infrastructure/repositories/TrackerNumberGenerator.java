package com.airedgesoft.sharedkernel.tracker.infrastructure.repositories;


@FunctionalInterface
public interface TrackerNumberGenerator {
    String generate(Long trackerId);
}
