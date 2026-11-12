package sk.workingset.application.port;

import sk.workingset.domain.LightTrackerItem;

public interface RequirementWritePort {
    /** 保存业务内容和记录保存凭据必须处于同一事务。 */
    void materialize(MaterializeRequest request, Runnable recordBusinessSave);

    void verifyMaterializedTarget(LightTrackerItem target);
}
