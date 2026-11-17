package sk.workingset.application;

import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.Tracker;

/**
 * 派生结果：新 Tracker（非共享派生身份 + 来源）与初始 Source-backed 的 LightTrackerItem。
 */
public record DeriveResult(Tracker tracker, LightTrackerItem item) {
}
