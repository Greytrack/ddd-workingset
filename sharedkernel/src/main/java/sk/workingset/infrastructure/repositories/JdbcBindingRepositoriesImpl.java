package sk.workingset.infrastructure.repositories;

import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.repository.ProductSpaceRepository;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

/** WorkingSet/Tracker/ProductSpace 的 JDBC 仓储实现；谱系查询带防环与根/非根约束。 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcBindingRepositoriesImpl implements WorkingSetRepository, TrackerRepository, ProductSpaceRepository {
    private static final String TRACKER_COLUMNS =
            "tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id, is_default";
    private static final RowMapper<Tracker> TRACKER_MAPPER = (rs, row) -> new Tracker(
            TrackerId.of(rs.getLong("tracker_id")), rs.getString("name"),
            optionalId(rs, "source_id"), WorkingSetId.of(rs.getLong("working_set_id")),
            rs.getLong("product_space_id"), rs.getLong("classification_id"),
            SharingMode.valueOf(rs.getString("sharing_mode")), optionalId(rs, "parent_id"),
            rs.getBoolean("is_default"));

    private final JdbcTemplate jdbc;


    @Override
    public Optional<WorkingSet> findWorkingSet(WorkingSetId id) {
        return loadReadyWorkingSet(id, false);
    }

    @Override
    public Optional<WorkingSet> lockWorkingSet(WorkingSetId id) {
        return loadReadyWorkingSet(id, true);
    }

    private Optional<WorkingSet> loadReadyWorkingSet(WorkingSetId id, boolean lock) {
        requireReady(id);
        return loadWorkingSet(id, lock);
    }

    @Override
    public Optional<WorkingSet> lockWorkingSetForCreation(WorkingSetId id) {
        return loadWorkingSet(id, true);
    }

    @Override
    public Optional<WorkingSet> findWorkingSetForCreation(WorkingSetId id) {
        return loadWorkingSet(id, false);
    }

    private Optional<WorkingSet> loadWorkingSet(WorkingSetId id, boolean lock) {
        String sql = "SELECT parent_working_set_id, name FROM working_sets WHERE working_set_id = ?";
        if (lock) {
            sql += " FOR UPDATE";
        }
        List<String[]> rows = jdbc.query(sql,
                (rs, row) -> new String[]{rs.getString("parent_working_set_id"), rs.getString("name")},
                id.numericValue());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<WorkingSet.SpaceBinding> spaces = jdbc.query("""
                SELECT sb.product_space_id, ps.name AS product_space_name, sb.origin, sb.state
                FROM space_bindings sb
                LEFT JOIN product_spaces ps ON ps.product_space_id = sb.product_space_id
                WHERE sb.working_set_id = ? ORDER BY sb.product_space_id
                """, (rs, row) -> new WorkingSet.SpaceBinding(id, rs.getLong("product_space_id"),
                rs.getString("product_space_name"),
                WorkingSet.SpaceOrigin.valueOf(rs.getString("origin")),
                WorkingSet.SpaceState.valueOf(rs.getString("state"))), id.numericValue());
        List<WorkingSet.TrackerBinding> trackers = jdbc.query("""
                SELECT tracker_id, product_space_id, classification_id, sharing_mode, origin FROM tracker_bindings
                WHERE working_set_id = ? ORDER BY product_space_id, classification_id
                """, (rs, row) -> new WorkingSet.TrackerBinding(id, TrackerId.of(rs.getLong("tracker_id")),
                rs.getLong("product_space_id"), rs.getLong("classification_id"),
                SharingMode.valueOf(rs.getString("sharing_mode")),
                WorkingSet.TrackerOrigin.valueOf(rs.getString("origin"))), id.numericValue());
        String parent = rows.getFirst()[0];
        String name = rows.getFirst()[1];
        return Optional.of(new WorkingSet(id, parent == null ? null : WorkingSetId.of(parent), name, spaces, trackers));
    }

    @Override
    public void requireReady(WorkingSetId id) {
        boolean preparing = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM working_set_creations
                    WHERE new_working_set_id = ? AND status IN ('Pending', 'Running')
                      AND COALESCE(result->'request'->>'mode', 'CREATE') = 'CREATE')
                """, Boolean.class, id.numericValue()));
        if (preparing) {
            throw DomainException.withMessage(FailureReason.PREPARING, id.value());
        }
    }

    @Override
    public boolean insertWorkingSet(WorkingSet workingSet) {
        return jdbc.update("""
                INSERT INTO working_sets (working_set_id, parent_working_set_id, name)
                VALUES (?, ?, ?) ON CONFLICT (working_set_id) DO NOTHING
                """, workingSet.id().numericValue(), workingSet.parentWorkingSetId().numericValue(), workingSet.name()) == 1;
    }

    @Override
    public List<WorkingSetId> findChildren(WorkingSetId id) {
        return jdbc.queryForList("""
                SELECT w.working_set_id FROM working_sets w
                WHERE parent_working_set_id = ? AND NOT EXISTS (
                    SELECT 1 FROM working_set_creations c WHERE c.new_working_set_id = w.working_set_id
                    AND c.status IN ('Pending', 'Running')
                    AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
                ORDER BY w.working_set_id
                """, Long.class, id.numericValue()).stream().map(WorkingSetId::of).toList();
    }

    @Override
    public List<WorkingSetId> findAncestors(WorkingSetId id) {
        List<Long> ancestors = jdbc.queryForList("""
                WITH RECURSIVE lineage(working_set_id, parent_working_set_id, distance) AS (
                    SELECT working_set_id, parent_working_set_id, 0
                    FROM working_sets WHERE working_set_id = ?
                    UNION ALL
                    SELECT parent.working_set_id, parent.parent_working_set_id, child.distance + 1
                    FROM working_sets parent JOIN lineage child ON parent.working_set_id = child.parent_working_set_id
                ) CYCLE working_set_id SET cyclic USING path
                SELECT working_set_id FROM lineage
                WHERE distance > 0 AND NOT EXISTS (
                    SELECT 1 FROM working_set_creations c
                    WHERE c.new_working_set_id = lineage.working_set_id
                    AND c.status IN ('Pending', 'Running')
                    AND COALESCE(c.result->'request'->>'mode', 'CREATE') = 'CREATE')
                ORDER BY distance
                """, Long.class, id.numericValue());
        if (!ancestors.isEmpty() && !WorkingSet.DEFAULT_ID.numericValue().equals(ancestors.getLast())) {
            throw new BindingFailure(Reason.WorkingSetCycle, id.value());
        }
        return ancestors.stream().map(WorkingSetId::of).toList();
    }

    @Override
    public void insertSpaceBinding(WorkingSet.SpaceBinding binding) {
        jdbc.update("""
                INSERT INTO space_bindings (working_set_id, product_space_id, origin, state) VALUES (?, ?, ?, ?)
                """, binding.workingSetId().numericValue(), binding.numericProductSpaceId(), binding.origin().name(),
                binding.state().name());
    }

    @Override
    public void insertTrackerBinding(WorkingSet.TrackerBinding binding) {
        jdbc.update("""
                INSERT INTO tracker_bindings
                    (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                VALUES (?, ?, ?, ?, ?, ?)
                """, binding.workingSetId().numericValue(), binding.trackerId().numericValue(), binding.numericProductSpaceId(),
                binding.numericClassificationId(), binding.sharingMode().name(), binding.origin().name());
    }

    @Override
    public Optional<Tracker> findTracker(TrackerId id) {
        return loadTracker(id, false);
    }

    @Override
    public Optional<Tracker> lockTracker(TrackerId id) {
        return loadTracker(id, true);
    }

    @Override
    public Optional<Tracker> findDerivedTracker(WorkingSetId target, TrackerId source) {
        return jdbc.query("""
                SELECT tracker_id FROM trackers WHERE working_set_id = ? AND source_id = ?
                """, (rs, row) -> TrackerId.of(rs.getLong(1)), target.numericValue(), source.numericValue())
                .stream().findFirst().flatMap(this::findTracker);
    }

    private Optional<Tracker> loadTracker(TrackerId id, boolean lock) {
        String sql = "SELECT " + TRACKER_COLUMNS + " FROM trackers WHERE tracker_id = ?";
        return jdbc.query(lock ? sql + " FOR UPDATE" : sql, TRACKER_MAPPER, id.numericValue()).stream().findFirst();
    }

    @Override
    public List<Tracker> findTrackers(WorkingSetId workingSetId) {
        return jdbc.query("SELECT " + TRACKER_COLUMNS + """
                 FROM trackers t
                 WHERE t.visible AND (t.working_set_id = ? OR EXISTS (
                     SELECT 1 FROM tracker_bindings b
                     WHERE b.working_set_id = ? AND b.tracker_id = t.tracker_id
                 ))
                 ORDER BY t.tracker_id
                """, TRACKER_MAPPER, workingSetId.numericValue(), workingSetId.numericValue());
    }

    @Override
    public void insertTracker(Tracker tracker) {
        insertTracker(tracker, true);
    }

    @Override
    public void insertInvisibleTracker(Tracker tracker) {
        insertTracker(tracker, false);
    }

    private void insertTracker(Tracker tracker, boolean visible) {
        jdbc.update("""
                INSERT INTO trackers
                    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id,
                     sharing_mode, parent_id, is_default, visible)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, tracker.trackerId().numericValue(), tracker.name(),
                tracker.sourceId() == null ? null : tracker.sourceId().numericValue(),
                tracker.workingSetId().numericValue(), tracker.numericProductSpaceId(), tracker.numericClassificationId(),
                tracker.sharingMode().name(), tracker.parentId() == null ? null : tracker.parentId().numericValue(),
                tracker.isDefault(), visible);
    }

    @Override
    public boolean isVisible(TrackerId trackerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT visible FROM trackers WHERE tracker_id = ?", Boolean.class, trackerId.numericValue()));
    }

    @Override
    public void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds) {
        for (TrackerId parent : parentTrackerIds) {
            jdbc.update("""
                    UPDATE trackers t SET visible = TRUE
                    WHERE t.working_set_id = ? AND t.source_id = ? AND NOT t.visible
                      AND EXISTS (SELECT 1 FROM tracker_bindings b
                                  WHERE b.working_set_id = t.working_set_id AND b.tracker_id = t.tracker_id)
                    """, target.numericValue(), parent.numericValue());
        }
    }

    @Override
    public Optional<ProductSpace> lockProductSpace(String id) {
        return jdbc.query("SELECT product_space_id, name FROM product_spaces WHERE product_space_id = ? FOR UPDATE",
                (rs, row) -> new ProductSpace(rs.getLong(1), rs.getString(2)), Long.valueOf(id)).stream().findFirst();
    }

    @Override
    public Optional<Classification> findClassification(String id) {
        return jdbc.query("SELECT classification_id, sharing_mode, name FROM classifications WHERE classification_id = ?",
                (rs, row) -> new Classification(rs.getLong(1), SharingMode.valueOf(rs.getString(2)), rs.getString(3)),
                Long.valueOf(id)).stream().findFirst();
    }

    @Override
    public List<Classification> findClassifications() {
        return jdbc.query("SELECT classification_id, sharing_mode, name FROM classifications ORDER BY classification_id",
                (rs, row) -> new Classification(rs.getLong(1), SharingMode.valueOf(rs.getString(2)), rs.getString(3)));
    }

    @Override
    public void insertProductSpace(ProductSpace space) {
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)",
                space.numericId(), space.name());
    }

    @Override
    public boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM trackers
                    WHERE working_set_id = ? AND product_space_id = ? AND classification_id = ? AND is_default)
                """, Boolean.class, workingSetId.numericValue(), Long.valueOf(spaceId), Long.valueOf(classificationId)));
    }

    @Override
    public void deleteTrackerBinding(WorkingSetId id, TrackerId trackerId) {
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ? AND tracker_id = ?",
                id.numericValue(), trackerId.numericValue());
    }

    @Override
    public void deleteBindingsToTracker(TrackerId trackerId) {
        jdbc.update("DELETE FROM tracker_bindings WHERE tracker_id = ?", trackerId.numericValue());
    }

    @Override
    public void deleteSpaceBinding(WorkingSetId id, String spaceId) {
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ? AND product_space_id = ?",
                id.numericValue(), Long.valueOf(spaceId));
    }

    @Override
    public List<WorkingSetId> findWorkingSetsBoundToSpace(String spaceId) {
        return jdbc.query("SELECT working_set_id FROM space_bindings WHERE product_space_id = ? ORDER BY working_set_id",
                (rs, row) -> WorkingSetId.of(rs.getLong(1)), Long.valueOf(spaceId));
    }

    @Override
    public void deleteWorkingSet(WorkingSetId id) {
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id.numericValue());
    }

    @Override
    public boolean hasOrgChildren(TrackerId id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trackers WHERE parent_id = ?)", Boolean.class, id.numericValue()));
    }

    @Override
    public boolean hasDerivedChildren(TrackerId id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trackers WHERE source_id = ?)", Boolean.class, id.numericValue()));
    }

    @Override
    public boolean hasTrackersInSpace(String spaceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trackers WHERE product_space_id = ?)", Boolean.class, spaceId));
    }

    @Override
    public void deleteTracker(TrackerId id) {
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", id.numericValue());
    }

    @Override
    public void deleteProductSpace(String id) {
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", Long.valueOf(id));
    }

    private static TrackerId optionalId(ResultSet rs, String column) throws SQLException {
        String id = rs.getString(column);
        return id == null ? null : TrackerId.of(id);
    }
}
