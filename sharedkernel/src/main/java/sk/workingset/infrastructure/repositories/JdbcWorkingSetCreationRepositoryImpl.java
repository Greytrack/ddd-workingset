package sk.workingset.infrastructure.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sk.SharedKernelResultCode;
import sk.common.error.DomainException;
import sk.workingset.domain.workingset.WorkingSetCreation;
import sk.workingset.domain.workingset.WorkingSetCreationRepository;
import sk.workingset.domain.workingset.WorkingSetId;

import java.sql.Timestamp;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcWorkingSetCreationRepositoryImpl implements WorkingSetCreationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;


    @Override
    public boolean save(WorkingSetCreation creation) {
        return creation.status() == WorkingSetCreation.Status.Pending ? insert(creation) : update(creation);
    }

    private boolean insert(WorkingSetCreation creation) {
        WorkingSetCreation.Request request = creation.request();
        return jdbc.update("""
                        INSERT INTO working_set_creations
                            (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                        VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                        ON CONFLICT DO NOTHING
                        """, creation.numericId(), request.numericParentWorkingSetId(), request.numericTargetWorkingSetId(), request.requestId(),
                creation.status().name(), encode(creation), Timestamp.from(creation.createdAt())) == 1;
    }

    @Override
    public Optional<WorkingSetCreation> find(String id) {
        return load("SELECT result FROM working_set_creations WHERE id = ?", Long.valueOf(id));
    }

    @Override
    public Optional<WorkingSetCreation> findForUpdate(String id) {
        return load("SELECT result FROM working_set_creations WHERE id = ? FOR UPDATE", Long.valueOf(id));
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

    private boolean update(WorkingSetCreation creation) {
        return jdbc.update("""
                        UPDATE working_set_creations
                        SET status = ?, result = CAST(? AS jsonb), started_at = ?, completed_at = ?
                        WHERE id = ?
                        """, creation.status().name(), encode(creation),
                Timestamp.from(creation.startedAt()),
                creation.completedAt() == null ? null : Timestamp.from(creation.completedAt()), creation.numericId()) == 1;
    }

    private Optional<WorkingSetCreation> load(String sql, Object id) {
        return jdbc.query(sql, (rs, row) -> decode(rs.getString(1)), id).stream().findFirst();
    }

    private String encode(WorkingSetCreation creation) {
        try {
            return mapper.writeValueAsString(creation);
        } catch (JsonProcessingException failure) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_SERIALIZATION_FAILED, failure);
        }
    }

    private WorkingSetCreation decode(String result) {
        try {
            return mapper.readValue(result, WorkingSetCreation.class);
        } catch (JsonProcessingException failure) {
            throw new DomainException(SharedKernelResultCode.PERSISTENCE_SERIALIZATION_FAILED, failure);
        }
    }
}
