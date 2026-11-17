package sk.workingset.application.event;

import java.time.Instant;
import java.util.Objects;

import sk.workingset.application.port.MergeTarget;

/**
 * Merge 业务写入已提交的事实（D15-B，SK 定义、Merge 执行发布）。
 *
 * <p>SK 据此在独立事务中完成目标变体切换/创建；事件处理幂等，
 * 未完成时由 Modulith 事件发布记录重投。
 */
public record RequirementMergeApplied(MergeTarget target, Instant occurredAt) {

    public RequirementMergeApplied {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
