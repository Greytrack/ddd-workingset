package com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem;


import org.jmolecules.ddd.annotation.Entity;

import java.util.Objects;

/**
 * An indexed business revision, identified by its revision within the owning item.
 */
@Entity
public record LightTrackerItemRevisionEntity(Integer revision) {
    public LightTrackerItemRevisionEntity {
        Objects.requireNonNull(revision, "revision");
    }
}
