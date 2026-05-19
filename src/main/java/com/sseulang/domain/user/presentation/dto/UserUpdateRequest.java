package com.sseulang.domain.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "본인 프로필 partial update — null 필드는 변경 X.")
public record UserUpdateRequest(
        @Schema(description = "프로필 이미지 URL/key. null=변경 X, 빈 문자열=제거",
                example = "https://cdn.sseulang.com/profiles/100/avatar.jpg")
        String profileImage,

        @Schema(description = "닉네임 1~50자. null=변경 X", example = "쓸랭이")
        @Size(min = 1, max = 50)
        String nickname
) {}
