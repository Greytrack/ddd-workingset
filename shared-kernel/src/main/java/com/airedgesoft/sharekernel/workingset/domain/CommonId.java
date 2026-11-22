package com.airedgesoft.sharekernel.workingset.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;

/**
 * 家族身份：同一 Item 家族在派生链中保持不变（FACT-005、INV-040）。
 */
@ValueObject
public record CommonId(@JsonProperty("value") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericValue) {

    public CommonId {
        Objects.requireNonNull(numericValue, "commonId");
        if (numericValue <= 0) {
            throw new IllegalArgumentException("commonId must be a positive integer");
        }
    }

    public static CommonId of(String value) {
        return of(Long.parseLong(value));
    }

    public static CommonId of(long value) {
        return new CommonId(value);
    }

    public String value() {
        return numericValue.toString();
    }
}
