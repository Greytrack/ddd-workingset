package com.airedgesoft.sharedkernel.workingset.infrastructure.providers;

import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowFailureLogQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationCompletedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reads business failures from their durable requests and outcomes. */
@Component
@RequiredArgsConstructor
public class CowFailureLogQueryProviderImpl implements CowFailureLogQueryProvider {
    private final JdbcTemplate jdbc;

    @Override
    public List<Failure> findBySource(Long sourceLocalId) {
        return jdbc.query("""
                WITH requests AS (
                    SELECT serialized_event::jsonb AS payload
                    FROM event_publication
                    WHERE event_type = ? AND serialized_event::jsonb ->> 'sourceLocalId' = ?
                ), failures AS (
                    SELECT DISTINCT ON (request.payload ->> 'eventId', request.payload ->> 'targetLocalId')
                           request.payload AS request_payload, result.serialized_event::jsonb AS result_payload,
                           result.publication_date
                    FROM requests request
                    JOIN event_publication result
                      ON result.event_type = ?
                     AND result.serialized_event::jsonb ->> 'eventId' = request.payload ->> 'eventId'
                     AND result.serialized_event::jsonb ->> 'targetLocalId' = request.payload ->> 'targetLocalId'
                     AND result.serialized_event::jsonb ->> 'outcome' = 'FAILED'
                    ORDER BY request.payload ->> 'eventId', request.payload ->> 'targetLocalId',
                             result.publication_date, result.id
                )
                SELECT (request_payload ->> 'eventId')::bigint AS event_id,
                       (request_payload ->> 'targetLocalId')::bigint AS target_local_id,
                       (request_payload ->> 'beforeRevision')::integer AS before_revision,
                       (request_payload ->> 'afterRevision')::integer AS after_revision,
                       (request_payload ->> 'beforeLockVersion')::bigint AS before_lock_version,
                       (request_payload ->> 'afterLockVersion')::bigint AS after_lock_version,
                       result_payload ->> 'failure' AS message, publication_date
                FROM failures ORDER BY publication_date, event_id, target_local_id
                """, (row, number) -> new Failure(row.getLong("event_id"), row.getLong("target_local_id"),
                sourceLocalId, row.getInt("before_revision"), row.getInt("after_revision"),
                row.getObject("before_lock_version", Long.class), row.getObject("after_lock_version", Long.class),
                1, "MATERIALIZE", "MATERIALIZATION_FAILED", row.getString("message"),
                row.getTimestamp("publication_date").getTime()),
                TrackerItemMaterializationRequestedEvent.class.getName(), sourceLocalId.toString(),
                TrackerItemMaterializationCompletedEvent.class.getName());
    }
}
