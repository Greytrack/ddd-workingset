package com.airedgesoft.alm.requirement.infrastructure.providers;


import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RequirementTrackerItemCatalogQueryProvider implements TrackerItemCatalogQueryProvider {
    private static final RowMapper<Identity> IDENTITY = (row, rowNumber) -> new Identity(
            row.getLong("local_id"), row.getLong("common_id"),
            row.getInt("revision"));

    private final JdbcTemplate jdbc;

    @Override
    public boolean hasAnyItem(Long trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM requirements WHERE tracker_id = ?)",
                Boolean.class, trackerId));
    }

    @Override
    public List<Identity> identities(Long trackerId) {
        return jdbc.query("""
                SELECT local_id, common_id, revision FROM requirements
                WHERE tracker_id = ? AND NOT delete_flag
                ORDER BY local_id
                """, IDENTITY, trackerId);
    }

    @Override
    public List<Identity> identitiesForMerge(Long trackerId) {
        return jdbc.query("""
                SELECT local_id, common_id, revision FROM requirements
                WHERE tracker_id = ?
                ORDER BY local_id
                """, IDENTITY, trackerId);
    }

}
