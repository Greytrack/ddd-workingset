package sk.productspace.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.productspace.domain.ProductSpace;

import java.util.Optional;

/**
 * ProductSpace 聚合生命周期仓储。
 */
@Repository
public interface ProductSpaceRepository {
    /**
     * 保存聚合（新增或更新）。
     */
    void save(ProductSpace space);

    /**
     * 命令侧重建聚合；读模型查询使用 Query Provider。
     */
    Optional<ProductSpace> findById(String id);

    /**
     * Checks retained business instances, including soft-deleted instances.
     */
    boolean hasBusinessReferences(ProductSpace space);

    /**
     * 永久删除聚合。
     */
    void delete(ProductSpace space);
}
