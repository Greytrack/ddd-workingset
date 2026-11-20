package sk.workingset.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JdbcClassificationCatalog implements ClassificationCatalog {
    private static final RowMapper<Classification> CLASSIFICATION = (row, position) ->
            new Classification(row.getLong(1), SharingMode.valueOf(row.getString(2)), row.getString(3),
                    row.getLong(4), row.getLong(5), row.getLong(6));
    private static final String SELECT = "SELECT classification_id, sharing_mode, name, base_class, class, type FROM classifications";

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Classification> findClassification(String id) {
        return jdbc.query(SELECT + " WHERE classification_id = ?",
                CLASSIFICATION, Long.valueOf(id)).stream().findFirst();
    }

    @Override
    public List<Classification> findClassifications() {
        return jdbc.query(SELECT + " ORDER BY classification_id",
                CLASSIFICATION);
    }
}
