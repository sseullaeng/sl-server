package com.sseulang.domain.admin.presentation.dto;

import com.sseulang.domain.admin.domain.Admin;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 본인 정보 — 프론트가 admin 로그인 후 store 초기화에 사용.
 *
 * <p>일반 사용자 {@code MeResponse} 와 별도 — admin 은 잔액/리뷰/email 등 사용자 자원이 없고,
 * 시스템상 {@code admins} 와 {@code users} 테이블이 분리되어 있다 (admin chain ROLE_ADMIN 만 통과).</p>
 */
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

    /** OAuth allowlist 매치 admin — users 테이블 기반. email 을 username 자리에, nickname 을 name 자리에. */
    public static AdminMeResponse fromUser(com.sseulang.domain.user.domain.User u) {
        return new AdminMeResponse(u.getId(), u.getEmail(), u.getNickname(), "ADMIN");
    }
}
