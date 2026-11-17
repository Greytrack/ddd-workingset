package sk.workingset.application;

import java.util.Objects;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * 非共享派生请求：来源 Tracker + 来源变体 + 选定的业务版次；不携带业务内容快照（派生不复制内容）。
 */
public record DeriveCommand(WorkingSetId workingSetId,
                            TrackerId sourceTrackerId,
                            LocalId sourceLocalId,
                            Revision sourceRevision,
                            CommonId commonId) {

    public DeriveCommand {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(sourceTrackerId, "sourceTrackerId");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(commonId, "commonId");
    }
}
