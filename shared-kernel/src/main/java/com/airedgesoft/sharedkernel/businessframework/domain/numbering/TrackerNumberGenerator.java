package com.airedgesoft.sharedkernel.businessframework.domain.numbering;


@FunctionalInterface
public interface TrackerNumberGenerator {
    String generate(Long trackerId);
}
