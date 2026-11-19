package sk.workingset.infrastructure.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import sk.workingset.domain.merge.MergeDecisionVersionGenerator;

/** 基于数据库序列的 Merge 决策版本分配。 */
@Component
@RequiredArgsConstructor
public class JdbcMergeDecisionVersionGenerator implements MergeDecisionVersionGenerator {
    private final JdbcTemplate jdbc;

    @Override
    public long nextDecisionVersion() {
        return jdbc.queryForObject("SELECT nextval('merge_decision_version')", Long.class);
    }
}
