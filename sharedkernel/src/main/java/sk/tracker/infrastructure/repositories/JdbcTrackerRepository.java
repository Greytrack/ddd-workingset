package sk.tracker.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import sk.RequestThreadHolder;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSetId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcTrackerRepository implements TrackerRepository {
    private static final String COLUMNS = """
            t.id, t.common_id, t.name, t.source_branch_id, t.workingset_id, t.work_space,
            t.tracker_class, t.share, NULLIF(t.parent_id, 0) AS parent_id, t.is_default
            """;
    private static final RowMapper<Tracker> MAPPER = (rs, row) -> Tracker.reconstitute(
            TrackerId.of(rs.getLong("id")), TrackerId.of(rs.getLong("common_id")),
            rs.getString("name"), optionalId(rs, "source_branch_id"), WorkingSetId.of(rs.getLong("workingset_id")),
            rs.getLong("work_space"), rs.getLong("tracker_class"),
            rs.getInt("share") == 1 ? SharingMode.SHARED : SharingMode.UNSHARED,
            optionalId(rs, "parent_id"), rs.getInt("is_default") == 1);

    private final JdbcTemplate jdbc;
    private final TrackerNumberGenerator numbers;
    private final JdbcCommonInstanceStore commonInstances;

    private static void requireOne(int affected, long id) {
        if (affected != 1) {
            throw new IncorrectUpdateSemanticsDataAccessException("Tracker rows are inconsistent for ID " + id);
        }
    }

    private static TrackerId optionalId(ResultSet rs, String column) throws SQLException {
        Long id = rs.getObject(column, Long.class);
        return id == null ? null : TrackerId.of(id);
    }

    private static Long value(TrackerId id) {
        return id == null ? null : id.numericValue();
    }

    private static long currentActor() {
        return Objects.requireNonNull(RequestThreadHolder.getLongUserId(), "Tracker write requires a current user");
    }

    @Override
    public Optional<Tracker> findById(TrackerId id) {
        return load(id, false);
    }

    @Override
    public Optional<Tracker> lockTracker(TrackerId id) {
        return load(id, true);
    }

    private Optional<Tracker> load(TrackerId id, boolean lock) {
        return jdbc.query("SELECT " + COLUMNS + """
                        FROM air_inst_tracker_rev t WHERE t.id = ? AND t.delete_flag = 0
                        AND t.work_space = COALESCE(CAST(? AS bigint), t.work_space)
                        """ + (lock ? " FOR UPDATE" : ""), MAPPER, id.numericValue(), RequestThreadHolder.getWorkspace())
                .stream().findFirst();
    }

    @Override
    public Optional<Tracker> findDerivedTracker(WorkingSetId target, TrackerId source) {
        return jdbc.query("SELECT " + COLUMNS + """
                        FROM air_inst_tracker_rev t
                        WHERE t.workingset_id = ? AND t.source_branch_id = ? AND t.delete_flag = 0
                          AND t.work_space = COALESCE(CAST(? AS bigint), t.work_space)
                        """, MAPPER, target.numericValue(), source.numericValue(), RequestThreadHolder.getWorkspace())
                .stream().findFirst();
    }

    @Override
    public Optional<Tracker> findRootFolder(WorkingSetId owner, String spaceId) {
        return jdbc.query("SELECT " + COLUMNS + """
                        FROM air_inst_tracker_rev t
                        WHERE t.workingset_id = ? AND t.work_space = ? AND t.tracker_class = ?
                          AND NULLIF(t.parent_id, 0) IS NULL AND t.share = 0 AND t.delete_flag = 0
                        """, MAPPER, owner.numericValue(), Long.valueOf(spaceId), Tracker.FOLDER_CLASSIFICATION_ID)
                .stream().findFirst();
    }

    @Override
    @Transactional
    public void save(Tracker tracker) {
        save(tracker, true, null);
    }

    @Override
    @Transactional
    public void save(Tracker tracker, boolean initiallyVisible, Long actor) {
        long writeActor = actor == null ? currentActor() : actor;
        if (tracker.isNew()) {
            insert(tracker, initiallyVisible ? 1 : 0, writeActor);
        } else {
            update(tracker, writeActor);
        }
    }

    private void insert(Tracker tracker, int visible, long actor) {
        long id = tracker.trackerId().numericValue();
        String number = numbers.generate(tracker.trackerId());
        String auditActor = Long.toString(actor);
        long now = System.currentTimeMillis();
        Long parent = value(tracker.parentId());
        Long source = value(tracker.sourceId());
        long workset = tracker.workingSetId().numericValue();
        int shared = tracker.sharingMode() == SharingMode.SHARED ? 1 : 0;
        int defaultFlag = tracker.isDefault() ? 1 : 0;
        jdbc.update("""
                        INSERT INTO air_inst_tracker_rev (id, instance_id, common_id, workingset_id, work_space,
                            base_class, class, type, tracker_class, tracker, default_layout, instance_type,
                            name, number, owner, share, revision_id, revision, version_type,
                            parent_id, source_branch_id, is_default, visible, locked, delete_flag, lock_version,
                            created_by, created_time, updated_by, updated_time)
                        VALUES (?, ?, ?, ?, ?, 1009, 100901, 10090101, ?, 584578917040480, 584532998805760, 0,
                            ?, ?, ?, ?, NULL, 1, 10001, ?, ?, ?, ?, 0, 0, 0, ?, ?, ?, ?)
                        """, id, id, tracker.commonId().numericValue(), workset, tracker.numericProductSpaceId(),
                tracker.numericClassificationId(), tracker.name(), number, actor, shared, parent, source,
                defaultFlag, visible, auditActor, now, auditActor, now);
        jdbc.update("""
                        INSERT INTO air_ws_tracker (id, workingset_id, sub_class_id, name, number,
                            parent_id, source_branch_id, shared, is_default, visible, locked, delete_flag, lock_version,
                            created_by, created_time, updated_by, updated_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?, ?, ?)
                        """, tracker.compatibility().trackerId().numericValue(), workset,
                tracker.compatibility().originalClassificationId(), tracker.name(), number,
                parent, source, shared, defaultFlag, visible, auditActor, now, auditActor, now);
        saveCommonInstance(id);
    }

    private void update(Tracker tracker, long actor) {
        long id = tracker.trackerId().numericValue();
        requireOne(jdbc.update("""
                        UPDATE air_inst_tracker_rev
                        SET name = ?, updated_by = ?, updated_time = ?, lock_version = lock_version + 1
                        WHERE id = ? AND work_space = ? AND visible = 1 AND delete_flag = 0
                        """, tracker.name(), Long.toString(actor), System.currentTimeMillis(),
                id, tracker.numericProductSpaceId()), id);
        requireOne(jdbc.update("""
                UPDATE air_ws_tracker c
                SET name = t.name, lock_version = t.lock_version,
                    updated_by = t.updated_by, updated_time = t.updated_time
                FROM air_inst_tracker_rev t
                WHERE t.id = ? AND c.id = t.id AND c.lock_version = t.lock_version - 1
                """, id), id);
        saveCommonInstance(id);
    }

    private void saveCommonInstance(long id) {
        commonInstances.save(jdbc.queryForObject("""
                SELECT id, base_class, class, type, work_space, number, name, NULL::BIGINT AS status, version, revision,
                       delete_flag, lock_version, created_by, created_time, updated_by, updated_time,
                       checked_out, owner, common_id, workingset_id, parent_id,
                       584578917040480 AS tracker, 1::BIGINT AS max_revision
                FROM air_inst_tracker_rev WHERE id = ?
                """, JdbcCommonInstanceStore.ROW_MAPPER, id));
    }

    @Override
    public boolean isVisible(TrackerId trackerId) {
        return exists("id = ? AND visible = 1 AND work_space = COALESCE(CAST(? AS bigint), work_space)",
                trackerId.numericValue(), RequestThreadHolder.getWorkspace());
    }

    @Override
    @Transactional
    public void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds, long actor) {
        for (TrackerId parent : parentTrackerIds) {
            List<Long> ids = jdbc.queryForList("""
                            UPDATE air_inst_tracker_rev t
                            SET visible = 1, lock_version = lock_version + 1, updated_by = ?, updated_time = ?
                            WHERE t.workingset_id = ? AND t.source_branch_id = ? AND t.delete_flag = 0 AND t.visible = 0
                              AND EXISTS (SELECT 1 FROM tracker_bindings b
                                          WHERE b.working_set_id = t.workingset_id AND b.tracker_id = t.id)
                            RETURNING t.id
                            """, Long.class, Long.toString(actor), System.currentTimeMillis(),
                    target.numericValue(), parent.numericValue());
            for (long id : ids) {
                requireOne(jdbc.update("""
                        UPDATE air_ws_tracker c
                        SET visible = t.visible, lock_version = t.lock_version,
                            updated_by = t.updated_by, updated_time = t.updated_time
                        FROM air_inst_tracker_rev t
                        WHERE t.id = ? AND c.id = t.id AND c.lock_version = t.lock_version - 1
                        """, id), id);
                saveCommonInstance(id);
            }
        }
    }

    @Override
    public boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId) {
        return exists("workingset_id = ? AND work_space = ? AND tracker_class = ? AND is_default = 1",
                workingSetId.numericValue(), Long.valueOf(spaceId), Long.valueOf(classificationId));
    }

    @Override
    public boolean hasOrgChildren(TrackerId id) {
        return exists("parent_id = ?", id.numericValue());
    }

    @Override
    public boolean hasDerivedChildren(TrackerId id) {
        return exists("source_branch_id = ?", id.numericValue());
    }

    @Override
    public boolean hasTrackersInSpace(String spaceId) {
        return exists("work_space = ?", Long.valueOf(spaceId));
    }

    private boolean exists(String predicate, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_inst_tracker_rev WHERE delete_flag = 0 AND " + predicate + ")",
                Boolean.class, args));
    }

    @Override
    @Transactional
    public void deleteTracker(TrackerId id) {
        if (lockTracker(id).isEmpty()) {
            return;
        }
        requireOne(jdbc.update("DELETE FROM air_ws_tracker WHERE id = ?", id.numericValue()), id.numericValue());
        requireOne(jdbc.update("DELETE FROM air_inst_tracker_rev WHERE id = ?", id.numericValue()), id.numericValue());
        commonInstances.remove(id.numericValue());
    }
}
