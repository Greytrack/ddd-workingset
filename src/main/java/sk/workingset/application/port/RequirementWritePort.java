package sk.workingset.application.port;

import java.util.List;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

public interface RequirementWritePort {
    /**
     * 保存业务对象（D3-B：业务保存独立提交）。
     *
     * <p>{@code target} 是 SK 提供的目标变体身份证据；业务 BC 不得直接读写 SK 聚合。
     * 业务保存提交后，SK 再以独立事务记录物化凭据并切换 LTI。
     */
    void materialize(MaterializeRequest request, TargetIdentity target);

    void verifyMaterializedTarget(TargetIdentity target);

    void lockMergeEvidence(List<LocalId> ids);

    Revision applyMerge(RequirementMergeCommand command);
}
