package com.airedgesoft.sharedkernel.workingset.domain;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;

/**
 * 最小分类元数据。共享策略由分类初始化时确定。
 */
@ValueObject
public record Classification(Long numericId, SharingMode sharingMode, String name,
                             long baseClassId, long classId) {
    public Classification {
        if (numericId == null || numericId <= 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "classificationId must be positive");
        }
        Objects.requireNonNull(sharingMode, "sharingMode");
    }

    public String id() {
        return numericId.toString();
    }

    public boolean allows(SharingMode mode) {
        return mode == SharingMode.SHARED || mode == sharingMode;
    }

    public enum SharingMode {SHARED, UNSHARED}
}
