package sk.workingset.domain;

import java.util.Objects;

/**
 * 家族身份：同一 Item 家族在派生链中保持不变（FACT-005、INV-040）。
 */
public record CommonId(String value) {

    public CommonId {
        Objects.requireNonNull(value, "commonId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("commonId must not be blank");
        }
    }

    public static CommonId of(String value) {
        return new CommonId(value);
    }
}
