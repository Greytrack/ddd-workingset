package alm.requirement.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import alm.requirement.application.command.RequirementMergeReceiptStore;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.event.MergeEntryResolvedEvent;
import sk.workingset.domain.event.MergeEntryResolvedEvent.Outcome;

/** requirement_merge_receipts 的 JDBC 存储。 */
@Repository
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
