package sk.workingset.application.port;

import java.util.List;

import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

public interface RequirementWritePort {
    /** 保存业务内容和记录保存凭据必须处于同一事务。 */
    void materialize(MaterializeRequest request, Runnable recordBusinessSave);

    void verifyMaterializedTarget(LightTrackerItem target);

    void lockMergeEvidence(List<LocalId> ids);

    Revision applyMerge(RequirementMergeCommand command);
}
