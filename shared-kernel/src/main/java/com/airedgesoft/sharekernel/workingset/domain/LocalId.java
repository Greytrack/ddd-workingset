package com.airedgesoft.sharekernel.workingset.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jmolecules.ddd.annotation.ValueObject;

import java.util.Objects;

/**
 * 所有业务对象聚合的唯一标识（用户 2026-11-11 裁定：LocalId 即业务聚合标识，RequirementId = LocalId）。
 * <p>由 {@code IdentifierGenerator} 以雪花 ID 产生（FACT-005）；派生时预分配，首次 COW 复用，
 * COW 前后不变（T3/A2、INV-040）。
 */
@ValueObject
public record LocalId(@JsonProperty("value") @JsonFormat(shape = JsonFormat.Shape.STRING) Long numericValue) {

    public LocalId {
        Objects.requireNonNull(numericValue, "localId");
        if (numericValue <= 0) {
            throw new IllegalArgumentException("localId must be a positive integer");
        }
    }

    public static LocalId of(String value) {
        return of(Long.parseLong(value));
    }

    public static LocalId of(long value) {
        return new LocalId(value);
    }

    public String value() {
        return numericValue.toString();
    }
}
