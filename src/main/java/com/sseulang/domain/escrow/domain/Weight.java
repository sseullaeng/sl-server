package com.sseulang.domain.escrow.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    
    public boolean isTruck() {
        return truck;
    }

    @JsonCreator
    public static Weight fromCode(String code) {
        for (Weight w : values()) if (w.code.equals(code)) return w;
        throw new IllegalArgumentException("Unknown Weight: " + code);
    }
}
