package com.sseulang.domain.escrow.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 물품 무게 enum. 5kg 이상은 용달차 분기 (truck fee 적용).
 * JSON value 는 프론트와 일치 (lt1 / 1to3 / 3to5 / 5to10 / gt10).
 */
public enum Weight {
    LT1("lt1", 1.0, false),
    R1TO3("1to3", 1.2, false),
    R3TO5("3to5", 1.5, false),
    R5TO10("5to10", 2.0, true),
    GT10("gt10", 2.5, true);

    private final String code;
    private final double multiplier;
    private final boolean truck;

    Weight(String code, double multiplier, boolean truck) {
        this.code = code;
        this.multiplier = multiplier;
        this.truck = truck;
    }

    @JsonProperty
    public String code() {
        return code;
    }

    public double multiplier() {
        return multiplier;
    }

    /** 5kg 이상이면 용달차 요금 분기 — 결정 #9 (CC3 프론트 schema 와 동일). */
    public boolean isTruck() {
        return truck;
    }

    @JsonCreator
    public static Weight fromCode(String code) {
        for (Weight w : values()) if (w.code.equals(code)) return w;
        throw new IllegalArgumentException("Unknown Weight: " + code);
    }
}
