package sk.productspace.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.productspace.domain.ProductSpace;

import java.util.Optional;

/**
 * ProductSpace 聚合存取。
 */
@Repository
public interface ProductSpaceRepository {
    Optional<ProductSpace> lockProductSpace(String id);

    void save(ProductSpace space);

    void deleteProductSpace(String id);
}
