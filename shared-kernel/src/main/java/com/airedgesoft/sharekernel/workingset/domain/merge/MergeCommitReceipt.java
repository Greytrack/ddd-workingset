package com.airedgesoft.sharekernel.workingset.domain.merge;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * 已提交的条目事实，供轮次恢复和 Checkpoint 使用。
 */
@ValueObject
public record MergeCommitReceipt(Long businessRef, Integer sourceRevision, Integer targetRevision) {
}
