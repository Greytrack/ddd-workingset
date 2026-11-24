package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.sharedkernel.businessframework.application.event.EventConsumption;
import org.springframework.stereotype.Component;

@Component
public class EventConsumptionImpl implements EventConsumption {

    @Override
    public void consume(String id, Runnable action) {

    }

}
