package sk.productspace.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.productspace.domain.ProductSpace;

import java.util.Optional;

/** ProductSpace 聚合生命周期仓储。 */
@Repository
public interface ProductSpaceRepository {
    void save(ProductSpace space);

    /** 命令侧重建聚合；读模型查询使用 Query Provider。 */
    Optional<ProductSpace> findById(String id);

    void delete(ProductSpace space);
}
