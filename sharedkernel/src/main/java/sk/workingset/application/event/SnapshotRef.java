package sk.workingset.application.event;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 不可变快照引用（P4）：SK 公共信封只携带引用，不解释、不复制业务内容。
 *
 * <p>快照内容由事件来源 BC 自行定义与保存；SK 在消费时凭该引用获取「修改前」内容。
 */
public record SnapshotRef(@JsonProperty("id") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericId) {

    public SnapshotRef {
        Objects.requireNonNull(numericId, "snapshotRef.id");
        if (numericId <= 0) {
            throw new IllegalArgumentException("snapshotRef.id must be positive");
        }
    }

    public String id() { return numericId.toString(); }

    public static SnapshotRef of(String id) {
        return new SnapshotRef(Long.valueOf(id));
    }
}
