package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * Tracker 的工作集归属与共享模式查询（D10，SK 定义、SK 实现）。
 *
 * <p>业务 BC 创建 Item 时据此解析归属：共享 Tracker 实际归属默认工作集，
 * 非共享 Tracker 归属其所在工作集；业务 BC 不得直接访问 SK 仓储。
 */
public interface TrackerScopePort {

    Optional<TrackerScope> find(TrackerId trackerId);

    record TrackerScope(WorkingSetId workingSetId, SharingMode sharingMode) {}
}
