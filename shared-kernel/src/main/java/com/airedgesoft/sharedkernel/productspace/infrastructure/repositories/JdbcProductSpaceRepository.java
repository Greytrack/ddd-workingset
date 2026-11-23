package com.airedgesoft.sharedkernel.productspace.infrastructure.repositories;

import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.productspace.domain.repository.ProductSpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ProductSpace 聚合的 PostgreSQL/Spring JDBC 仓储。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcProductSpaceRepository implements ProductSpaceRepository {
    private final JdbcTemplate jdbc;
    private final JdbcCommonInstanceStore commonInstances;

    @Override
    public Optional<ProductSpace> findById(String id) {
        return load(id, " FOR NO KEY UPDATE");
    }

    @Override
    public Optional<ProductSpace> findByIdForBinding(String id) {
        return load(id, " FOR KEY SHARE");
    }

    @Override
    public Optional<ProductSpace> findByIdForDeletion(String id) {
        return load(id, " FOR UPDATE");
    }

    private Optional<ProductSpace> load(String id, String lockingClause) {
        return jdbc.query("""
                        SELECT product_space_id, name FROM product_spaces
                        WHERE product_space_id = ? AND delete_flag = 0
                        """ + lockingClause,
                        (row, position) -> new ProductSpace(row.getLong(1), row.getString(2)), Long.valueOf(id))
                .stream().findFirst();
    }

    @Override
    public void save(ProductSpace space) {
        long id = space.numericId();
        if (space.deleted()) {
            jdbc.update("UPDATE product_spaces SET delete_flag = 1 WHERE product_space_id = ?", id);
            jdbc.update("UPDATE air_inst_common SET delete_flag = 1 WHERE id = ?", id);
            return;
        }
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)", id, space.name());
        commonInstances.save(new AirInstCommonPo(id, 1003L, 100301L, 10030101L,
                id, null, space.name(), null, null, 1L, 0, null, null, null, null, null,
                null, null, id, 1L, null, 584189545406816L, 1L));
    }

    @Override
    public boolean hasBusinessReferences(ProductSpace space) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM air_inst_common instance
                    WHERE instance.work_space = ? AND instance.id <> ?
                      AND NOT EXISTS (SELECT 1 FROM air_inst_tracker_rev tracker WHERE tracker.id = instance.id)
                )
                """, Boolean.class, space.numericId(), space.numericId()));
    }

}
