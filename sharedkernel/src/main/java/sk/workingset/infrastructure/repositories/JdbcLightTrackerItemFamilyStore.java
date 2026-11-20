package sk.workingset.infrastructure.repositories;

import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import sk.workingset.application.port.LightTrackerItemFamilyStore;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;

/**
 * LightTrackerItem family 的原子认领及继承摘要写入；索引流程专用。
 */
@Component
@RequiredArgsConstructor
public class JdbcLightTrackerItemFamilyStore implements LightTrackerItemFamilyStore {
    private final JdbcTemplate jdbc;
    private final JdbcLightTrackerItemRepositoryImpl index;
    private final JdbcCommonInstanceStore commonInstances;

    @Override
    public boolean claim(LightTrackerItem item) {
        boolean inserted = index.claimFamily(item);
        if (inserted && !item.materialized()) {
            saveInheritedCommonInstance(item);
        }
        return inserted;
    }

    private void saveInheritedCommonInstance(LightTrackerItem item) {
        AirInstCommonPo common = jdbc.queryForObject("""
                        SELECT target.work_space, source.number,
                               CASE WHEN source.revision = ? THEN COALESCE(source.name, source.title) END AS name,
                               CASE WHEN source.revision = ? THEN source.version END AS version
                        FROM requirements source JOIN air_inst_tracker_rev target ON target.id = ?
                        WHERE source.local_id = ? AND NOT EXISTS (SELECT 1 FROM requirements WHERE local_id = ?)
                        """, (rs, row) -> new AirInstCommonPo(item.localId().numericValue(), 1401L, 140101L, 14010101L,
                        rs.getObject("work_space", Long.class), rs.getString("number"), rs.getString("name"),
                        null, rs.getString("version"), (long) item.revision().value(), 0,
                        null, null, null, null, null, null, null, item.commonId().numericValue(),
                        item.workingSetId().numericValue(), null, item.trackerId().numericValue(),
                        (long) item.revision().value()),
                item.sourceRevision().value(), item.sourceRevision().value(), item.trackerId().numericValue(),
                item.sourceLocalId().numericValue(), item.localId().numericValue());
        commonInstances.save(common);
    }
}
