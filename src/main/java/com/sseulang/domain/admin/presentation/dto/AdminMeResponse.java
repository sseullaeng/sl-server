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
        @Schema(example = "admin") String username,
        @Schema(example = "관리자") String name,
        @Schema(example = "ADMIN", description = "항상 ADMIN — 별도 권한 분리는 R2 영역") String role
) {
    public static AdminMeResponse from(Admin a) {
        return new AdminMeResponse(a.getId(), a.getUsername(), a.getName(), a.getRole());
    }
}
