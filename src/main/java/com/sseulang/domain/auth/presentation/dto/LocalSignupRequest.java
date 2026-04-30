package com.sseulang.domain.auth.presentation.dto;

import com.sseulang.domain.user.domain.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * LOCAL 가입 요청. 비밀번호는 BCrypt 72-byte limit + 보안 정책으로 8~72자.
 * 길이/형식 추가 검증은 LocalAuthService 가 도메인 룰로 한 번 더 가드.
 */
public record LocalSignupRequest(
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 100) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 50) String nickname
) {
    public Email toEmailVO() {
        return new Email(email);
    }
}
