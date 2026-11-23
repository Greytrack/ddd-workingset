package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * 来源业务对象发生普通修改的公共事件信封（P4，SK 定义、ALM 发布）。
 *
 * <p>携带公共元数据与修改前的 JSON 快照；业务字段由来源 BC 解释。{@code eventId} 由雪花生成器产生。
 * 事件描述已经发生的事实：业务内容已保存成功后才发布。
 *
 * @param eventId           事件身份（雪花 ID）
 * @param requirementId     来源业务对象身份（首切片 RequirementId = LocalId）
 * @param sourceLocalId     实际内容来源变体身份
 * @param beforeRevision    修改前业务版次
 * @param afterRevision     提交后业务版次
 * @param beforeLockVersion 修改前并发版本
 * @param afterLockVersion  提交后并发版本
 * @param snapshot          修改前不可变快照
 * @param occurredAt        事件发生时间
 */
public record BusinessObjectUpdateEvent(long eventId,
                                        @JsonFormat(shape = JsonFormat.Shape.STRING) Long requirementId,
                                        @JsonFormat(shape = JsonFormat.Shape.STRING) Long sourceLocalId,
                                        Integer beforeRevision,
                                        Integer afterRevision,
                                        Long beforeLockVersion,
                                        Long afterLockVersion,
                                        JsonNode snapshot,
                                        long occurredAt) {

    public BusinessObjectUpdateEvent {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(beforeRevision, "beforeRevision");
        Objects.requireNonNull(afterRevision, "afterRevision");
        Objects.requireNonNull(beforeLockVersion, "beforeLockVersion");
        Objects.requireNonNull(afterLockVersion, "afterLockVersion");
        snapshot = Objects.requireNonNull(snapshot, "snapshot").deepCopy();
    }

    @Override
    public JsonNode snapshot() {
        return snapshot.deepCopy();
    }
}
