package sk.workingset.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

/**
 * ProductSpace 是 Shared Kernel 独立聚合：只维护稳定归属身份。
 *
 * <p>产品空间只能归属默认工作集；其创建入口中的工作集参数不参与归属选择，
 * 空间绑定与默认 Tracker 投影由默认工作集维护。name 仅用于界面可读展示。
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

    public String id() { return numericId.toString(); }
}
