package com.airedgesoft.sharekernel.productspace.domain.repository;

import com.airedgesoft.sharekernel.productspace.domain.ProductSpace;
import org.jmolecules.ddd.annotation.Repository;

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
