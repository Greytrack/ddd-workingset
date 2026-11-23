package com.airedgesoft.sharedkernel.workingset.infrastructure.providers;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowRequestPublications;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads request evidence even after the business listener completed delivery. */
@Component
@RequiredArgsConstructor
public class JdbcCowRequestPublications implements CowRequestPublications {

    private static final String REQUEST_TYPE = TrackerItemMaterializationRequestedEvent.class.getName();

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Override
    public boolean exists(Long sourceLocalId, Long targetLocalId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM event_publication
                    WHERE event_type = ? AND serialized_event::jsonb ->> 'sourceLocalId' = ?
                      AND serialized_event::jsonb ->> 'targetLocalId' = ?
                )
                """, Boolean.class, REQUEST_TYPE, sourceLocalId.toString(), targetLocalId.toString()));
    }

    @Override
    public Optional<TrackerItemMaterializationRequestedEvent> find(long eventId, Long targetLocalId) {
        return jdbc.query("""
                SELECT serialized_event FROM event_publication
                WHERE event_type = ? AND serialized_event::jsonb ->> 'eventId' = ?
                  AND serialized_event::jsonb ->> 'targetLocalId' = ?
                LIMIT 1
                """, (rs, rowNum) -> decode(rs.getString("serialized_event")),
                REQUEST_TYPE, Long.toString(eventId), targetLocalId.toString()).stream().findFirst();
    }

    private TrackerItemMaterializationRequestedEvent decode(String payload) {
        try {
            return json.readValue(payload, TrackerItemMaterializationRequestedEvent.class);
        } catch (JsonProcessingException ex) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_SERIALIZATION_FAILED, ex);
        }
    }
}
