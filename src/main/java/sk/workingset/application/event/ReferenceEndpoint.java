package sk.workingset.application.event;

import java.util.Objects;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * Reference 端点。source 端必须完整；target 的 local/workingSet/revision 可为空，
 * 表示目标尚未纳入或悬空引用。
 */
public record ReferenceEndpoint(WorkingSetId workingSetId, LocalId localId, CommonId commonId,
                                TrackerId trackerId, Revision revision) {

    public ReferenceEndpoint {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }

    public boolean isResolved() {
        return workingSetId != null && localId != null;
    }
}
