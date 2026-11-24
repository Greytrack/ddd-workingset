package com.airedgesoft.sharedkernel.businessframework.domain.numbering;

import org.springframework.stereotype.Component;

/**
 * Temporary deterministic implementation until the platform numbering service is wired.
 */
@Component
public class LocalTrackerNumberGenerator implements TrackerNumberGenerator {
    @Override
    public String generate(Long trackerId) {
        return "TRK-" + trackerId;
    }
}
