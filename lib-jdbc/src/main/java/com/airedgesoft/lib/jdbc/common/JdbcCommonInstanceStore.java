package com.airedgesoft.lib.jdbc.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class JdbcCommonInstanceStore {
    private static final String COLUMNS = """
            id, base_class, class, type, work_space, number, name, status, version, revision, delete_flag, lock_version,
            created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id, parent_id,
            tracker, max_revision
            """;

    public static final RowMapper<AirInstCommonPo> ROW_MAPPER = (rs, row) -> new AirInstCommonPo(
            rs.getObject("id", Long.class), rs.getObject("base_class", Long.class),
            rs.getObject("class", Long.class), rs.getObject("type", Long.class),
            rs.getObject("work_space", Long.class), rs.getString("number"), rs.getString("name"),
            rs.getObject("status", Long.class), rs.getString("version"), rs.getObject("revision", Long.class),
            rs.getObject("delete_flag", Integer.class), rs.getObject("lock_version", Long.class),
            rs.getString("created_by"), rs.getObject("created_time", Long.class),
            rs.getString("updated_by"), rs.getObject("updated_time", Long.class),
            rs.getObject("checked_out", Integer.class), rs.getObject("owner", Long.class),
            rs.getObject("common_id", Long.class), rs.getObject("workingset_id", Long.class),
            rs.getObject("parent_id", Long.class), rs.getObject("tracker", Long.class),
            rs.getObject("max_revision", Long.class));

    private static final String SAVE = "INSERT INTO air_inst_common AS saved (" + COLUMNS + """
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                work_space = COALESCE(EXCLUDED.work_space, saved.work_space),
                number = COALESCE(EXCLUDED.number, saved.number),
                name = EXCLUDED.name,
                status = COALESCE(EXCLUDED.status, saved.status),
                version = EXCLUDED.version,
                revision = EXCLUDED.revision,
                delete_flag = EXCLUDED.delete_flag,
                lock_version = COALESCE(EXCLUDED.lock_version, saved.lock_version),
                created_by = COALESCE(EXCLUDED.created_by, saved.created_by),
                created_time = COALESCE(EXCLUDED.created_time, saved.created_time),
                updated_by = COALESCE(EXCLUDED.updated_by, saved.updated_by),
                updated_time = COALESCE(EXCLUDED.updated_time, saved.updated_time),
                checked_out = EXCLUDED.checked_out,
                owner = COALESCE(EXCLUDED.owner, saved.owner),
                common_id = COALESCE(EXCLUDED.common_id, saved.common_id),
                workingset_id = COALESCE(EXCLUDED.workingset_id, saved.workingset_id),
                parent_id = EXCLUDED.parent_id,
                tracker = EXCLUDED.tracker,
                max_revision = GREATEST(saved.max_revision, EXCLUDED.max_revision)
            WHERE saved.base_class = EXCLUDED.base_class
              AND saved.class IS NOT DISTINCT FROM EXCLUDED.class AND saved.type IS NOT DISTINCT FROM EXCLUDED.type
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcCommonInstanceStore(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    // The owning repository or its item savepoint decides how the write failure rolls back.
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = RuntimeException.class)
    public void save(AirInstCommonPo row) {
        int affected = jdbc.update(SAVE, row.id(), row.baseClass(), row.clazz(), row.type(), row.workSpace(),
                row.number(), row.name(), row.status(), row.version(), row.revision(), row.deleteFlag(), row.lockVersion(),
                row.createdBy(), row.createdTime(), row.updatedBy(), row.updatedTime(), row.checkedOut(), row.owner(),
                row.commonId(), row.workingSetId(), row.parentId(), row.tracker(), row.maxRevision());
        if (affected == 0) {
            throw new DataIntegrityViolationException("Common instance ID belongs to another type: " + row.id());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remove(long id) {
        jdbc.update("DELETE FROM air_inst_common WHERE id = ?", id);
    }

    public Optional<AirInstCommonPo> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM air_inst_common WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<AirInstCommonPo> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query("SELECT " + COLUMNS + " FROM air_inst_common WHERE id IN (:ids)",
                Map.of("ids", keys), ROW_MAPPER);
    }

}
