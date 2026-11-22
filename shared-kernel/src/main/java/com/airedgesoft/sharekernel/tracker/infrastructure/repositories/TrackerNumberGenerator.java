package com.airedgesoft.sharekernel.tracker.infrastructure.repositories;


@FunctionalInterface
public interface TrackerNumberGenerator {
    String generate(Long trackerId);
}
