package com.sseulang.domain.admin.presentation.dto;

import com.sseulang.domain.admin.domain.Admin;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 본인 정보 (헤더/store 초기화용).")
public record AdminMeResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "admin", description = "admins 테이블이면 username, OAuth allowlist 매치면 email") String username,
        @Schema(example = "관리자", description = "admins 면 admin.name, OAuth 면 user.nickname") String name,
        @Schema(example = "ADMIN", description = "항상 ADMIN — 별도 권한 분리는 R2 영역") String role
) {
    public static AdminMeResponse from(Admin a) {
        return new AdminMeResponse(a.getId(), a.getUsername(), a.getName(), a.getRole());
    }

    
    public static AdminMeResponse fromUser(com.sseulang.domain.user.domain.User u) {
        return new AdminMeResponse(u.getId(), u.getEmail(), u.getNickname(), "ADMIN");
    }
}
