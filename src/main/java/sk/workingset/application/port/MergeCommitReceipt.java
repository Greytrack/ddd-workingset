package sk.workingset.application.port;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/** 已提交的条目事实，供轮次恢复和 Checkpoint 使用。 */
public record MergeCommitReceipt(LocalId businessRef, Revision sourceRevision, Revision targetRevision) {}
