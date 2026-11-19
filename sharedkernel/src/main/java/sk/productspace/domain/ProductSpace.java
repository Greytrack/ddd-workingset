package sk.productspace.domain;


import org.jmolecules.ddd.annotation.AggregateRoot;
import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

/**
 * ProductSpace 是 Shared Kernel 独立聚合：只维护稳定归属身份。
 *
 * <p>产品空间在默认工作集维护生命周期；空间绑定由各 Working Set 持有。
 */
@AggregateRoot
public record ProductSpace(Long numericId, String name) {
    public ProductSpace(String id) {
        this(id, null);
    }

    public ProductSpace(String id, String name) {
        this(Long.valueOf(id), name);
    }

    public ProductSpace {
        if (numericId == null || numericId <= 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "productSpaceId must be positive");
        }
    }

    public String id() {
        return numericId.toString();
    }
}
