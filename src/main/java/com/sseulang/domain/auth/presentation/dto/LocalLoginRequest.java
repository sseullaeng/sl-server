package com.sseulang.domain.auth.presentation.dto;

import com.sseulang.domain.user.domain.Email;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "LOCAL 가입자 로그인. 성공 시 AT/RT 쿠키 발급.")
public record LocalLoginRequest(
        @Schema(description = "가입 이메일", example = "alice@sseulang.test", maxLength = 100)
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 100) String email,

        @Schema(description = "비밀번호", example = "P@ssw0rd!", maxLength = 72)
        @NotBlank @Size(max = 72) String password
) {
    public Email toEmailVO() {
        return new Email(email);
    }
}
