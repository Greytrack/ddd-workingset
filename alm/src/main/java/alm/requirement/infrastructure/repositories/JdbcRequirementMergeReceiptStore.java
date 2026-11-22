package alm.requirement.infrastructure.repositories;

import alm.requirement.application.command.RequirementMergeReceiptStore;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryResolvedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryResolvedEvent.Outcome;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * requirement_merge_receipts 的 JDBC 存储。
 */
@Component
@RequiredArgsConstructor
public class JdbcRequirementMergeReceiptStore implements RequirementMergeReceiptStore {
    private final JdbcTemplate jdbc;


    @Override
    public Optional<MergeEntryResolvedEvent> find(String roundId, String entryId) {
        return jdbc.query("""
                        SELECT outcome, business_ref, source_revision, target_revision, created, inherited, failure
                        FROM requirement_merge_receipts WHERE round_id = ? AND entry_id = ?
                        """, (rs, row) -> new MergeEntryResolvedEvent(roundId, entryId,
                        Outcome.valueOf(rs.getString("outcome")),
                        rs.getObject("business_ref", Long.class) == null ? null : LocalId.of(rs.getLong("business_ref")),
                        Revision.of(rs.getInt("source_revision")), Revision.of(rs.getInt("target_revision")),
                        rs.getBoolean("created"), rs.getBoolean("inherited"), rs.getString("failure")),
                Long.valueOf(roundId), Long.valueOf(entryId)).stream().findFirst();
    }

    @Override
    public void save(MergeEntryResolvedEvent result) {
        jdbc.update("""
                        INSERT INTO requirement_merge_receipts
                            (round_id, entry_id, outcome, business_ref, source_revision, target_revision,
                             created, inherited, failure)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Long.valueOf(result.roundId()), Long.valueOf(result.entryId()), result.outcome().name(),
                result.businessRef() == null ? null : result.businessRef().numericValue(),
                result.sourceRevision().value(), result.targetRevision().value(),
                result.created(), result.inherited(), result.failure());
    }
}
