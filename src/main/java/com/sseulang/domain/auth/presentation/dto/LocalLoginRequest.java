package com.sseulang.domain.auth.presentation.dto;

import com.sseulang.domain.user.domain.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalLoginRequest(
        @NotBlank @jakarta.validation.constraints.Email @Size(max = 100) String email,
        @NotBlank @Size(max = 72) String password
) {
    public Email toEmailVO() {
        return new Email(email);
    }
}
