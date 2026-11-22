package com.airedgesoft.sharekernel.workingset.domain.merge;

import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 已提交的条目事实，供轮次恢复和 Checkpoint 使用。
 */
@ValueObject
public record MergeCommitReceipt(LocalId businessRef, Revision sourceRevision, Revision targetRevision) {
}
