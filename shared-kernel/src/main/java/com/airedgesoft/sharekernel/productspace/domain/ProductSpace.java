package com.airedgesoft.sharekernel.productspace.domain;


import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.productspace.domain.event.ProductSpaceDeleted;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import org.jmolecules.ddd.annotation.AggregateRoot;

/**
 * ProductSpace 是 Shared Kernel 独立聚合：只维护稳定归属身份。
 *
 * <p>产品空间在默认工作集维护生命周期；空间绑定由各 Working Set 持有。
 */
@AggregateRoot
public final class ProductSpace {
    private final Long numericId;
    private final String name;
    private boolean deleted;

    public ProductSpace(String id) {
        this(id, null);
    }

    public ProductSpace(String id, String name) {
        this(Long.valueOf(id), name);
    }

    public ProductSpace(Long numericId, String name) {
        if (numericId == null || numericId <= 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "productSpaceId must be positive");
        }
        this.numericId = numericId;
        this.name = name;
    }

    public String id() {
        return numericId.toString();
    }

    public Long numericId() {
        return numericId;
    }

    public String name() {
        return name;
    }

    public boolean deleted() {
        return deleted;
    }

    /**
     * 标记删除并产生领域事实。
     */
    public ProductSpaceDeleted delete() {
        if (deleted) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "ProductSpace is already deleted");
        }
        deleted = true;
        return new ProductSpaceDeleted(numericId);
    }
}
