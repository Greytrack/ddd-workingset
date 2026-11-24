package com.airedgesoft.sharedkernel.workingset.domain;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;

/** Sharing policy selected for a tracker or binding. */
public enum SharingMode {
    SHARED, UNSHARED;

    public static SharingMode from(ClassSnapshot classification) {
        return classification.isShared() ? SHARED : UNSHARED;
    }

    public boolean isAllowedBy(ClassSnapshot classification) {
        return this == SHARED || this == from(classification);
    }
}
