package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackerCreatedEventContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesBooleanSharingAndReadsOutstandingEvents() throws Exception {
        TrackerCreatedEvent event = new TrackerCreatedEvent(2L, 1L, "3", "4", true);
        String json = mapper.writeValueAsString(event);
        JsonNode payload = mapper.readTree(json);
        assertTrue(payload.path("shared").isBoolean());
        assertFalse(payload.has("sharingMode"));
        assertEquals(event, mapper.readValue(json, TrackerCreatedEvent.class));

        String old = """
                {"trackerId":"2","workingSetId":"1","productSpaceId":"3","classificationId":"4","sharingMode":"%s"}
                """;
        assertEquals(event, mapper.readValue(old.formatted("SHARED"), TrackerCreatedEvent.class));
        assertFalse(mapper.readValue(old.formatted("UNSHARED"), TrackerCreatedEvent.class).shared());
    }
}
