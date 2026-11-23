package com.airedgesoft.alm.requirement.infrastructure.repositories;

import com.airedgesoft.alm.requirement.application.port.RequirementMergeResultPublications;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryResolvedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcRequirementMergeResultPublications implements RequirementMergeResultPublications {
    private final JdbcTemplate jdbc;

    @Override
    public void lockEntry(String roundId, String entryId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> { }, "requirement-merge:" + roundId + ":" + entryId);
    }

    @Override
    public boolean exists(String roundId, String entryId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM event_publication
                    WHERE event_type = ? AND serialized_event::jsonb ->> 'roundId' = ?
                      AND serialized_event::jsonb ->> 'entryId' = ?
                )
                """, Boolean.class, MergeEntryResolvedEvent.class.getName(), roundId, entryId));
    }
}
