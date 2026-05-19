package com.sseulang.domain.user.domain;

import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 100;
    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        
        value = value.trim().toLowerCase();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("이메일은 100자 이하여야 합니다");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }
    }
}
