package com.airedgesoft.sharedkernel.productspace.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * ProductSpace 聚合已删除。
 */
public record ProductSpaceDeleted(@JsonFormat(shape = JsonFormat.Shape.STRING) Long productSpaceId) {
    public ProductSpaceDeleted {
        Objects.requireNonNull(productSpaceId, "productSpaceId");
    }
}
