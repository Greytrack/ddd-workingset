package com.airedgesoft.sharedkernel.workingset.domain;

import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergeEventJsonContractTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void usesScalarJsonWithoutLosingSnowflakePrecision() throws Exception {
        TrackerItemCreatedEvent event = new TrackerItemCreatedEvent(
                9_007_199_254_740_993L, 9_007_199_254_740_994L, 4L, 1L, 257);
        String scalarInput = """
                {"localId":"9007199254740993","commonId":"9007199254740994","trackerId":4,
                 "workingSetId":1,"revision":257}
                """;
        assertEquals(event, json.readValue(scalarInput, TrackerItemCreatedEvent.class));

        String persisted = json.writeValueAsString(event);
        JsonNode payload = json.readTree(persisted);
        assertEquals("9007199254740993", payload.path("localId").textValue());
        assertEquals(257, payload.path("revision").intValue());
        assertEquals(event, json.readValue(persisted, TrackerItemCreatedEvent.class));
    }
}
