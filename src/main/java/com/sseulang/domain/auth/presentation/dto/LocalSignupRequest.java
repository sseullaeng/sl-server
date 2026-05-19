package com.sseulang.domain.auth.presentation.dto;

import com.sseulang.domain.user.domain.Email;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 + 비밀번호 + 닉네임으로 LOCAL 가입")
public record LocalSignupRequest(
        @Schema(description = "이메일 (최대 100자, RFC 5322 형식)", example = "alice@sseulang.test", maxLength = 100)
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 100) String email,

        @Schema(description = "비밀번호 (8~72자, BCrypt 72-byte limit)", example = "P@ssw0rd!", minLength = 8, maxLength = 72)
        @NotBlank @Size(min = 8, max = 72) String password,

        @Schema(description = "닉네임 (최대 50자)", example = "쓸랭이", maxLength = 50)
        @NotBlank @Size(max = 50) String nickname
) {
    public Email toEmailVO() {
        return new Email(email);
    }
}
