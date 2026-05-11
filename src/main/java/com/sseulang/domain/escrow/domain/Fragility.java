package com.sseulang.domain.escrow.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Fragility {
    F1("f1", 1.0),
    F2("f2", 1.1),
    F3("f3", 1.2),
    F4("f4", 1.4),
    F5("f5", 1.6);

    private final String code;
    private final double multiplier;

    Fragility(String code, double multiplier) {
        this.code = code;
        this.multiplier = multiplier;
    }

    @JsonProperty
    public String code() {
        return code;
    }

    public double multiplier() {
        return multiplier;
    }

    @JsonCreator
    public static Fragility fromCode(String code) {
        for (Fragility f : values()) if (f.code.equals(code)) return f;
        throw new IllegalArgumentException("Unknown Fragility: " + code);
    }
}
