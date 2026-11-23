package com.airedgesoft.sharedkernel.productspace.infrastructure.repositories;

import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.sharedkernel.RequestThreadHolder;
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
                        SELECT id, name FROM air_inst_product_space
                        WHERE id = ? AND delete_flag = 0
                        """ + lockingClause,
                        (row, position) -> new ProductSpace(row.getLong(1), row.getString(2)), Long.valueOf(id))
                .stream().findFirst();
    }

    @Override
    public void save(ProductSpace space) {
        long id = space.numericId();
        String actor = RequestThreadHolder.getUserId();
        long now = System.currentTimeMillis();
        if (space.deleted()) {
            jdbc.update("""
                    UPDATE air_inst_product_space SET delete_flag = 1, updated_by = ?, updated_time = ? WHERE id = ?
                    """, actor, now, id);
            jdbc.update("UPDATE air_inst_common SET delete_flag = 1, updated_by = ?, updated_time = ? WHERE id = ?", actor, now, id);
            return;
        }
        jdbc.update("""
                INSERT INTO air_inst_product_space
                    (id, base_class, class, type, name, instance_id, common_id, work_space, workingset_id, tracker,
                     lock_version, delete_flag, created_by, created_time, updated_by, updated_time)
                VALUES (?, 1003, 100301, 10030101, ?, ?, ?, ?, 1, 584189545406816, 0, 0, ?, ?, ?, ?)
                """, id, space.name(), id, id, id, actor, now, actor, now);
        commonInstances.save(new AirInstCommonPo(id, 1003L, 100301L, 10030101L,
                id, null, space.name(), null, null, 1L, 0, 0L, actor, now, actor, now, null,
                null, id, 1L, null, 584189545406816L, 1L));
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
