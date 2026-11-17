package sk.workingset.application;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/**
 * 读取结果：内容 + 是否来自本地 + 实际读取的内容来源与版次（便于区分「读到来源」与「读到自己」）。
 */
public record ReadResult(TrackerItemContent content,
                         boolean materialized,
                         LocalId contentLocalId,
                         Revision contentRevision) {
}
