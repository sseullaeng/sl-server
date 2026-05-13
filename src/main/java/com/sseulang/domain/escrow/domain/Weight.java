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

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Weight fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("Weight code 는 필수입니다");
        String c = code.trim().toLowerCase(java.util.Locale.ROOT);
        // 정식 코드 우선 매칭
        for (Weight w : values()) if (w.code.equals(c)) return w;
        // 프론트 친숙한 alias 호환 (under1/over10 등)
        return switch (c) {
            case "under1" -> LT1;
            case "over10" -> GT10;
            default -> throw new IllegalArgumentException("Unknown Weight: " + code);
        };
    }
}
