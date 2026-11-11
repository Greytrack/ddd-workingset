package sk.workingset.application;

import java.util.Map;
import java.util.Objects;

import sk.workingset.domain.Revision;

/**
 * 读取结果内容（概念级）：业务字段由所属 BC 定义；SK 只透传，不解释业务语义。
 */
public record TrackerItemContent(Revision revision, Map<String, Object> fields) {

    public TrackerItemContent {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(fields, "fields");
        fields = Map.copyOf(fields);
    }
}
