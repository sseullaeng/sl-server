package com.sseulang.domain.escrow.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Volume {
    S("s", 1.0),
    M("m", 1.2),
    L("l", 1.5);

    private final String code;
    private final double multiplier;

    Volume(String code, double multiplier) {
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

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Volume fromCode(String code) {
        for (Volume v : values()) if (v.code.equals(code)) return v;
        throw new IllegalArgumentException("Unknown Volume: " + code);
    }
}
