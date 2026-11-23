package com.airedgesoft.alm.requirement.infrastructure.repositories;

import com.airedgesoft.alm.requirement.application.command.RequirementSnapshot;
import com.airedgesoft.alm.requirement.domain.RequirementSnapshotStore;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.SnapshotRefEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Reads the one source event shared by all target materialization requests. */
@Component
@RequiredArgsConstructor
public class JdbcRequirementSnapshotStore implements RequirementSnapshotStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    @Override
    public Optional<RequirementSnapshot> find(SnapshotRefEvent ref) {
        return jdbc.query("""
                SELECT serialized_event::jsonb ->> 'snapshot' AS snapshot
                FROM event_publication
                WHERE event_type = ? AND serialized_event::jsonb ->> 'eventId' = ?
                  AND serialized_event::jsonb ->> 'snapshot' IS NOT NULL
                LIMIT 1
                """, (rs, rowNum) -> decode(rs.getString("snapshot")),
                BusinessObjectUpdateEvent.class.getName(), ref.numericId().toString()).stream().findFirst();
    }

    private RequirementSnapshot decode(String snapshot) {
        try {
            return json.readValue(snapshot, RequirementSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_SERIALIZATION_FAILED, ex);
        }
    }
}
