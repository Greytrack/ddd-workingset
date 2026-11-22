package com.airedgesoft.sharekernel.productspace.domain.event;

import java.util.UUID;

/**
 * An empty product space was accepted for deletion.
 */
public record ProductSpaceDeletionRequested(String productSpaceId, UUID requestId, int step) {
    public ProductSpaceDeletionRequested nextStep() {
        return new ProductSpaceDeletionRequested(productSpaceId, requestId, step + 1);
    }
}
