package sk.workingset.domain.lighttrackeritem;

import org.jmolecules.ddd.annotation.Entity;
import sk.workingset.domain.Revision;

import java.util.Objects;

/** An indexed business revision, identified by its revision within the owning item. */
@Entity
public record LightTrackerItemRevisionEntity(Revision revision) {
    public LightTrackerItemRevisionEntity {
        Objects.requireNonNull(revision, "revision");
    }
}
