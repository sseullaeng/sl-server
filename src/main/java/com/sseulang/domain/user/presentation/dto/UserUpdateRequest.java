package com.sseulang.domain.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 본인 프로필 partial update. 모든 필드 optional — null 은 변경 안 함.
 *
 * <p>profileImage 빈 문자열은 "이미지 제거" 의도로 처리 (null 저장). 이미지 등록은 미인증 사용자도
 * 허용 — 5/2 합의 (자금/거래만 차단).</p>
 */
@Schema(description = "본인 프로필 partial update — null 필드는 변경 X.")
public record UserUpdateRequest(
        @Schema(description = "프로필 이미지 URL/key. null=변경 X, 빈 문자열=제거",
                example = "https://cdn.sseulang.com/profiles/100/avatar.jpg")
        String profileImage,

        @Schema(description = "닉네임 1~50자. null=변경 X", example = "쓸랭이")
        @Size(min = 1, max = 50)
        String nickname
) {}
