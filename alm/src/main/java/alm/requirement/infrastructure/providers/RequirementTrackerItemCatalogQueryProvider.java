package alm.requirement.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

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
    public List<Identity> page(TrackerId trackerId, String afterLocalId, int limit) {
        return jdbc.query("""
                SELECT local_id, common_id, revision FROM requirements
                WHERE tracker_id = ? AND NOT delete_flag AND local_id > ?
                ORDER BY local_id LIMIT ?
                """, IDENTITY, trackerId.numericValue(), afterLocalId.isEmpty() ? 0L : Long.parseLong(afterLocalId), limit);
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

    @Override
    public boolean hasActiveFamily(TrackerId trackerId, CommonId commonId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM requirements WHERE tracker_id = ? AND common_id = ? AND NOT delete_flag)
                """, Boolean.class, trackerId.numericValue(), commonId.numericValue()));
    }
}
