package bootstrap.web;

import java.util.Map;

import sk.workingset.application.ReadResult;

/** 读取结果的最小读模型。 */
public record ReadView(Map<String, Object> fields,
                       int contentRevision,
                       String contentLocalId,
                       boolean materialized) {

    public static ReadView from(ReadResult result) {
        return new ReadView(
                result.content().fields(),
                result.content().revision().value(),
                result.contentLocalId().value(),
                result.materialized());
    }
}
