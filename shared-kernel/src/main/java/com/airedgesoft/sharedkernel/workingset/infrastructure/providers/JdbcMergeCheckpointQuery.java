package com.airedgesoft.sharedkernel.workingset.infrastructure.providers;


import com.airedgesoft.sharedkernel.workingset.application.query.MergeCheckpoint;
import com.airedgesoft.sharedkernel.workingset.application.query.MergeCheckpointQuery;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.ChangeKind;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JdbcMergeCheckpointQuery implements MergeCheckpointQuery {
    private final JdbcTemplate jdbc;

    private static ChangeKind changeKind(String value) {
        return value == null ? null : switch (value) {
            case "ADD" -> ChangeKind.ADDED;
            case "UPDATED" -> ChangeKind.MODIFIED;
            case "DELETED" -> ChangeKind.DELETED;
            default -> throw new IllegalArgumentException("Unknown checkpoint difference type: " + value);
        };
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static Integer revision(Integer revision) {
        return revision == null ? null : revision;
    }

    private static Long localId(String localId) {
        return localId == null ? null : Long.valueOf(localId);
    }

    @Override
    public Optional<MergeCheckpoint> findLatest(MergeCheckpoint.Key key) {
        return jdbc.query("""
                        SELECT id, batch_id, local_id_a, local_id_b, revision_a, revision_b,
                               diff_type, merge_direction, merge_action, merge_status, operator
                        FROM air_ws_items_merge_index
                        WHERE branch_a_id = ? AND branch_b_id = ? AND common_id = ?
                        ORDER BY merge_seq DESC, id DESC LIMIT 1
                        """, (rs, row) -> new MergeCheckpoint(key, rs.getLong("id"),
                        revision(rs.getObject("revision_a", Integer.class)), revision(rs.getObject("revision_b", Integer.class)),
                        localId(rs.getString("local_id_a")), localId(rs.getString("local_id_b")), rs.getString("batch_id"),
                        changeKind(rs.getString("diff_type")),
                        enumValue(MergeCheckpoint.Direction.class, rs.getString("merge_direction")),
                        enumValue(MergeCheckpoint.Action.class, rs.getString("merge_action")),
                        !"MERGED".equals(rs.getString("merge_status")), rs.getString("operator")),
                key.a(), key.b(), key.family()).stream().findFirst();
    }
}
