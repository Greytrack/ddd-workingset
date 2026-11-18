package sk.workingset.application.common;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.WorkingSetId;

public record InheritedEditTarget(TargetIdentity identity, WorkingSetId workingSetId,
                                  LocalId sourceLocalId, Revision sourceRevision) {
}
