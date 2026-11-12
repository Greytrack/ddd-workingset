package sk.workingset.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import sk.workingset.application.port.ProductSpaceRepository;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

public final class JdbcBindingRepositories implements WorkingSetRepository, TrackerRepository, ProductSpaceRepository {
    private final JdbcTemplate jdbc;

    public JdbcBindingRepositories(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkingSet> findWorkingSet(WorkingSetId id) {
        return loadWorkingSet(id, false);
    }

    @Override
    public Optional<WorkingSet> lockWorkingSet(WorkingSetId id) {
        return loadWorkingSet(id, true);
    }

    private Optional<WorkingSet> loadWorkingSet(WorkingSetId id, boolean lock) {
        String sql = "SELECT parent_working_set_id FROM working_sets WHERE working_set_id = ?";
        if (lock) {
            sql += " FOR UPDATE";
        }
        var parents = jdbc.queryForList(sql, String.class, id.value());
        if (parents.isEmpty()) {
            return Optional.empty();
        }
        var spaces = jdbc.query("""
                SELECT product_space_id, origin, state FROM space_bindings
                WHERE working_set_id = ? ORDER BY product_space_id
                """, (rs, row) -> new WorkingSet.SpaceBinding(id, rs.getString("product_space_id"),
                WorkingSet.SpaceOrigin.valueOf(rs.getString("origin")),
                WorkingSet.SpaceState.valueOf(rs.getString("state"))), id.value());
        var trackers = jdbc.query("""
                SELECT tracker_id, product_space_id, classification_id, sharing_mode, origin FROM tracker_bindings
                WHERE working_set_id = ? ORDER BY product_space_id, classification_id
                """, (rs, row) -> new WorkingSet.TrackerBinding(id, TrackerId.of(rs.getString("tracker_id")),
                rs.getString("product_space_id"), rs.getString("classification_id"),
                SharingMode.valueOf(rs.getString("sharing_mode")),
                WorkingSet.TrackerOrigin.valueOf(rs.getString("origin"))), id.value());
        String parent = parents.getFirst();
        return Optional.of(new WorkingSet(id, parent == null ? null : WorkingSetId.of(parent), spaces, trackers));
    }

    @Override
    public boolean insertWorkingSet(WorkingSet workingSet) {
        return jdbc.update("""
                INSERT INTO working_sets (working_set_id, parent_working_set_id)
                VALUES (?, ?) ON CONFLICT (working_set_id) DO NOTHING
                """, workingSet.id().value(), workingSet.parentWorkingSetId().value()) == 1;
    }

    @Override
    public List<WorkingSetId> findChildren(WorkingSetId id) {
        return jdbc.queryForList("""
                SELECT working_set_id FROM working_sets
                WHERE parent_working_set_id = ? ORDER BY working_set_id
                """, String.class, id.value()).stream().map(WorkingSetId::of).toList();
    }

    @Override
    public List<WorkingSetId> findAncestors(WorkingSetId id) {
        var ancestors = jdbc.queryForList("""
                WITH RECURSIVE lineage(working_set_id, parent_working_set_id, distance) AS (
                    SELECT working_set_id, parent_working_set_id, 0
                    FROM working_sets WHERE working_set_id = ?
                    UNION ALL
                    SELECT parent.working_set_id, parent.parent_working_set_id, child.distance + 1
                    FROM working_sets parent JOIN lineage child ON parent.working_set_id = child.parent_working_set_id
                ) CYCLE working_set_id SET cyclic USING path
                SELECT working_set_id FROM lineage WHERE distance > 0 ORDER BY distance
                """, String.class, id.value());
        if (!ancestors.isEmpty() && !WorkingSet.DEFAULT_ID.value().equals(ancestors.getLast())) {
            throw new BindingFailure(Reason.WorkingSetCycle, id.value());
        }
        return ancestors.stream().map(WorkingSetId::of).toList();
    }

    @Override
    public void insertSpaceBinding(WorkingSet.SpaceBinding binding) {
        jdbc.update("""
                INSERT INTO space_bindings (working_set_id, product_space_id, origin, state) VALUES (?, ?, ?, ?)
                """, binding.workingSetId().value(), binding.productSpaceId(), binding.origin().name(),
                binding.state().name());
    }

    @Override
    public void insertTrackerBinding(WorkingSet.TrackerBinding binding) {
        jdbc.update("""
                INSERT INTO tracker_bindings
                    (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                VALUES (?, ?, ?, ?, ?, ?)
                """, binding.workingSetId().value(), binding.trackerId().value(), binding.productSpaceId(),
                binding.classificationId(), binding.sharingMode().name(), binding.origin().name());
    }

    @Override
    public Optional<Tracker> lockTracker(TrackerId id) {
        return jdbc.query("""
                SELECT tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode,
                       parent_id FROM trackers WHERE tracker_id = ? FOR UPDATE
                """, (rs, row) -> new Tracker(TrackerId.of(rs.getString("tracker_id")), optionalId(rs, "source_id"),
                WorkingSetId.of(rs.getString("working_set_id")), rs.getString("product_space_id"),
                rs.getString("classification_id"), SharingMode.valueOf(rs.getString("sharing_mode")),
                optionalId(rs, "parent_id")), id.value()).stream().findFirst();
    }

    @Override
    public void insertTracker(Tracker tracker) {
        jdbc.update("""
                INSERT INTO trackers
                    (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, tracker.trackerId().value(), tracker.sourceId() == null ? null : tracker.sourceId().value(),
                tracker.workingSetId().value(), tracker.productSpaceId(), tracker.classificationId(),
                tracker.sharingMode().name(), tracker.parentId() == null ? null : tracker.parentId().value());
    }

    @Override
    public Optional<ProductSpace> lockProductSpace(String id) {
        return jdbc.query("SELECT product_space_id FROM product_spaces WHERE product_space_id = ? FOR UPDATE",
                (rs, row) -> new ProductSpace(rs.getString(1)), id).stream().findFirst();
    }

    @Override
    public Optional<Classification> findClassification(String id) {
        return jdbc.query("SELECT classification_id, sharing_mode FROM classifications WHERE classification_id = ?",
                (rs, row) -> new Classification(rs.getString(1), SharingMode.valueOf(rs.getString(2))),
                id).stream().findFirst();
    }

    @Override
    public void deleteTrackerBinding(WorkingSetId id, TrackerId trackerId) {
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ? AND tracker_id = ?",
                id.value(), trackerId.value());
    }

    @Override
    public void deleteBindingsToTracker(TrackerId trackerId) {
        jdbc.update("DELETE FROM tracker_bindings WHERE tracker_id = ?", trackerId.value());
    }

    @Override
    public void deleteSpaceBinding(WorkingSetId id, String spaceId) {
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ? AND product_space_id = ?",
                id.value(), spaceId);
    }

    @Override
    public List<WorkingSetId> findWorkingSetsBoundToSpace(String spaceId) {
        return jdbc.query("SELECT working_set_id FROM space_bindings WHERE product_space_id = ? ORDER BY working_set_id",
                (rs, row) -> WorkingSetId.of(rs.getString(1)), spaceId);
    }

    @Override
    public void deleteWorkingSet(WorkingSetId id) {
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id.value());
    }

    @Override
    public boolean hasOrgChildren(TrackerId id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trackers WHERE parent_id = ?)", Boolean.class, id.value()));
    }

    @Override
    public boolean hasDerivedChildren(TrackerId id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trackers WHERE source_id = ?)", Boolean.class, id.value()));
    }

    @Override
    public boolean hasTrackersInSpace(String spaceId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM trackers WHERE product_space_id = ?)", Boolean.class, spaceId));
    }

    @Override
    public void deleteTracker(TrackerId id) {
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", id.value());
    }

    @Override
    public void deleteProductSpace(String id) {
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", id);
    }

    private static TrackerId optionalId(ResultSet rs, String column) throws SQLException {
        String id = rs.getString(column);
        return id == null ? null : TrackerId.of(id);
    }
}
