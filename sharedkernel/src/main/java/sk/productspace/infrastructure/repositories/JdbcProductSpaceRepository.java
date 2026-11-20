package sk.productspace.infrastructure.repositories;

import lombok.extern.slf4j.Slf4j;

import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;

import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcProductSpaceRepository implements ProductSpaceRepository {
    private final JdbcTemplate jdbc;
    private final JdbcCommonInstanceStore commonInstances;

    @Override
    public Optional<ProductSpace> findById(String id) {
        return jdbc.query("SELECT product_space_id, name FROM product_spaces WHERE product_space_id = ? FOR UPDATE",
                (row, position) -> new ProductSpace(row.getLong(1), row.getString(2)), Long.valueOf(id))
                .stream().findFirst();
    }

    @Override
    public void save(ProductSpace space) {
        long id = space.numericId();
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)", id, space.name());
        commonInstances.save(new AirInstCommonPo(id, 1003L, 100301L, 10030101L,
                id, null, space.name(), null, null, 1L, 0, null, null, null, null, null,
                null, null, id, 1L, null, 584189545406816L, 1L));
    }

    @Override
    public void delete(ProductSpace space) {
        long numericId = space.numericId();
        if (jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", numericId) == 1) {
            commonInstances.remove(numericId);
        }
    }
}
