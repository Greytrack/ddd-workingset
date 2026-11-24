package com.airedgesoft.sharedkernel.businessframework.domain.admin.productspace;

import lombok.Data;

/** 产品空间快照（替代 v3 的 {@code IProductSpace}，判权只用到类型）。 */
@Data
public class ProductSpaceSnapshot {

    /** 产品空间实例 ID */
    private Long id;

    /** 实例类型（v3 AirConstants 中的类型常量） */
    private Integer type;
}
