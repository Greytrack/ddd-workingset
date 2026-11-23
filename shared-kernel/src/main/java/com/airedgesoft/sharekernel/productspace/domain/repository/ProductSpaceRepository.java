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

    /** 保护挂载引用，并允许同空间的常规 Tracker 创建并行。 */
    Optional<ProductSpace> findByIdForBinding(String id);

    /** 在删除准入检查前，与正在建立的空间引用互斥。 */
    Optional<ProductSpace> findByIdForDeletion(String id);

    /**
     * Checks retained business instances, including soft-deleted instances.
     */
    boolean hasBusinessReferences(ProductSpace space);

}
