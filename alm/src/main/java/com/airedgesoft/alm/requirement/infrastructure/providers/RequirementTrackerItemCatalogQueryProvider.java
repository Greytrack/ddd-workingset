package com.airedgesoft.alm.requirement.infrastructure.providers;


import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RequirementTrackerItemCatalogQueryProvider implements TrackerItemCatalogQueryProvider {
    private static final RowMapper<Identity> IDENTITY = (row, rowNumber) -> new Identity(
            row.getLong("instance_id"), row.getLong("common_id"),
            row.getInt("revision"));

    private static final String CURRENT_IDENTITIES = """
            SELECT r.instance_id, r.common_id, r.revision FROM air_inst_requirement_rev r
            WHERE r.tracker = ?
              AND NOT EXISTS (
                  SELECT 1 FROM air_inst_requirement_rev newer
                  WHERE newer.instance_id = r.instance_id AND newer.revision > r.revision
              )
            """;

    private final JdbcTemplate jdbc;

    @Override
    public boolean hasAnyItem(Long trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_inst_requirement_rev WHERE tracker = ?)",
                Boolean.class, trackerId));
    }

    @Override
    public List<Identity> identities(Long trackerId) {
        return jdbc.query(CURRENT_IDENTITIES + "AND r.delete_flag = 0 ORDER BY r.instance_id", IDENTITY, trackerId);
    }

    @Override
    public List<Identity> identitiesForMerge(Long trackerId) {
        return jdbc.query(CURRENT_IDENTITIES + "ORDER BY r.instance_id", IDENTITY, trackerId);
    }

}
