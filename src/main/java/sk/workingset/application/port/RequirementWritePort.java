package sk.workingset.application.port;

import java.util.List;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

public interface RequirementWritePort {
    /**
     * 保存业务内容并记录保存凭据，二者必须处于调用方（SK）事务内。
     *
     * <p>{@code target} 是 SK 提供的目标变体身份证据；业务 BC 不得直接读写 SK 聚合。
     */
    void materialize(MaterializeRequest request, TargetIdentity target, Runnable recordBusinessSave);

    void verifyMaterializedTarget(TargetIdentity target);

    void lockMergeEvidence(List<LocalId> ids);

    Revision applyMerge(RequirementMergeCommand command);
}
