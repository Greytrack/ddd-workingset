package sk.workingset.domain.merge;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/**
 * 已提交的条目事实，供轮次恢复和 Checkpoint 使用。
 */
@ValueObject
public record MergeCommitReceipt(LocalId businessRef, Revision sourceRevision, Revision targetRevision) {
}
