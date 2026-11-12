package bootstrap.web;

/** 非共享派生结果的最小读模型。 */
public record DeriveView(String trackerId,
                         String sourceTrackerId,
                         LightTrackerItemView item) {
}
