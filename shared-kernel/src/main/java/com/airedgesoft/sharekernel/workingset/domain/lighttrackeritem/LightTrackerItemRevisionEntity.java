package com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem;

import com.airedgesoft.sharekernel.workingset.domain.Revision;
import org.jmolecules.ddd.annotation.Entity;

import java.util.Objects;

/**
 * An indexed business revision, identified by its revision within the owning item.
 */
@Entity
public record LightTrackerItemRevisionEntity(Revision revision) {
    public LightTrackerItemRevisionEntity {
        Objects.requireNonNull(revision, "revision");
    }
}
