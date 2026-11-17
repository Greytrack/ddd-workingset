package sk.workingset.infrastructure;

import java.sql.Timestamp;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import sk.workingset.application.port.WorkingSetCreationStore;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetId;

public final class JdbcWorkingSetCreationStore implements WorkingSetCreationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcWorkingSetCreationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public boolean insert(WorkingSetCreation creation) {
        var request = creation.request();
        return jdbc.update("""
                INSERT INTO working_set_creations
                    (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT DO NOTHING
                """, creation.numericId(), request.numericParentWorkingSetId(), request.numericTargetWorkingSetId(), request.requestId(),
                creation.status().name(), encode(creation), Timestamp.from(creation.createdAt())) == 1;
    }

    @Override
    public Optional<WorkingSetCreation> find(String id, boolean lock) {
        return load("SELECT result FROM working_set_creations WHERE id = ?" + (lock ? " FOR UPDATE" : ""), Long.valueOf(id));
    }

    @Override
    public Optional<WorkingSetCreation> findByTarget(WorkingSetId target) {
        return load("""
                SELECT result FROM working_set_creations
                WHERE new_working_set_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, target.numericValue());
    }

    @Override
    public Optional<WorkingSetCreation> findByRequest(String requestId) {
        return load("""
                SELECT result FROM working_set_creations
                WHERE request_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, requestId);
    }

    @Override
    public Optional<WorkingSetCreation> findActiveExtension(WorkingSetId target) {
        return load("""
                SELECT result FROM working_set_creations
                WHERE new_working_set_id = ? AND result->'request'->>'mode' = 'EXTEND'
                  AND status IN ('Pending', 'Running')
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, target.numericValue());
    }

    @Override
    public void update(WorkingSetCreation creation) {
        jdbc.update("""
                UPDATE working_set_creations
                SET status = ?, result = CAST(? AS jsonb), started_at = ?, completed_at = ?
                WHERE id = ?
                """, creation.status().name(), encode(creation),
                Timestamp.from(creation.startedAt()),
                creation.completedAt() == null ? null : Timestamp.from(creation.completedAt()), creation.numericId());
    }

    private Optional<WorkingSetCreation> load(String sql, Object id) {
        return jdbc.query(sql, (rs, row) -> decode(rs.getString(1)), id).stream().findFirst();
    }

    private String encode(WorkingSetCreation creation) {
        try {
            return mapper.writeValueAsString(creation);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode working set creation", failure);
        }
    }

    private WorkingSetCreation decode(String result) {
        try {
            return mapper.readValue(result, WorkingSetCreation.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot decode working set creation", failure);
        }
    }
}
