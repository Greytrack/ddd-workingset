package com.airedgesoft.alm.requirement.infrastructure.providers;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RequirementTrackerItemCatalogQueryProvider implements TrackerItemCatalogQueryProvider {
    private static final RowMapper<Identity> IDENTITY = (row, rowNumber) -> new Identity(
            LocalId.of(row.getLong("local_id")), CommonId.of(row.getLong("common_id")),
            Revision.of(row.getInt("revision")));

    private final JdbcTemplate jdbc;

    @Override
    public boolean hasAnyItem(TrackerId trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM requirements WHERE tracker_id = ?)",
                Boolean.class, trackerId.numericValue()));
    }

    @Override
    public List<Identity> identities(TrackerId trackerId) {
        return jdbc.query("""
                SELECT local_id, common_id, revision FROM requirements
                WHERE tracker_id = ? AND NOT delete_flag
                ORDER BY local_id
                """, IDENTITY, trackerId.numericValue());
    }

    @Override
    public List<Identity> identitiesForMerge(TrackerId trackerId) {
        return jdbc.query("""
                SELECT local_id, common_id, revision FROM requirements
                WHERE tracker_id = ?
                ORDER BY local_id
                """, IDENTITY, trackerId.numericValue());
    }

}
