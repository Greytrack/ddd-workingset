package sk.workingset.application.event;

import java.time.Instant;
import java.util.Objects;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/**
 * 来源业务对象发生普通修改的公共事件信封（P4，SK 定义、ALM 发布）。
 *
 * <p>只携带公共元数据与快照引用；业务字段由来源 BC 解释。{@code eventId} 由雪花生成器产生。
 * 事件描述已经发生的事实：业务内容已保存成功后才发布。
 *
 * @param eventId        事件身份（雪花 ID）
 * @param requirementId  来源业务对象身份（首切片 RequirementId = LocalId）
 * @param sourceLocalId  实际内容来源变体身份
 * @param revision       修改后的业务版次（普通修改不升级，仍为修改前版次）
 * @param snapshotRef    修改前不可变快照引用
 * @param occurredAt     事件发生时间
 */
public record RequirementChanged(long eventId,
                                 LocalId requirementId,
                                 LocalId sourceLocalId,
                                 Revision revision,
                                 SnapshotRef snapshotRef,
                                 Instant occurredAt) {

    public RequirementChanged {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(snapshotRef, "snapshotRef");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
