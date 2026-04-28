package com.sseulang.domain.user.domain;

import java.util.regex.Pattern;

/**
 * 이메일 VO. 불변 + 자가 검증.
 *
 * <p>JPA 매핑은 {@link User} 의 {@code String email} 컬럼이 책임지고, 도메인 layer 는
 * 의미적 wrapper 로 본 record 만 사용한다.</p>
 */
public record Email(String value) {

    private static final int MAX_LENGTH = 100;
    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        // 정규화: provider 별 대소문자 차이로 인한 중복 판단 흔들림 방지
        value = value.trim().toLowerCase();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("이메일은 100자 이하여야 합니다");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다");
        }
    }
}
