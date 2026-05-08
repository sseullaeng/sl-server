package com.sseulang.domain.banner.presentation.dto;

import com.sseulang.domain.banner.application.dto.BannerResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 메인 배너 응답. {@code adminId} 는 의도적으로 미노출 — 일반 사용자에게 운영자 식별 정보가
 * 새는 것을 차단 (audit 정보). admin 페이지의 banner 관리도 동일 schema 사용 (관리자가 누가
 * 만들었는지 식별할 필요 X — 필요 시 직접 DB 감사 로그 조회).
 */
@Schema(description = "메인 배너 — 클릭 시 linkUrl 로 이동. active+startsAt~endsAt 로 노출 제어.")
public record BannerResponse(
        @Schema(example = "2") Long id,
        @Schema(example = "5월 봄맞이 이벤트") String title,
        @Schema(example = "https://cdn.sseulang.com/banners/1/spring.jpg") String imageUrl,
        @Schema(example = "/events/spring") String linkUrl,
        @Schema(example = "1", description = "정렬 순서 (작을수록 앞)") int sortOrder,
        @Schema(example = "true") boolean active,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt
) {
    public static BannerResponse from(BannerResult r) {
        return new BannerResponse(r.id(), r.title(), r.imageUrl(), r.linkUrl(),
                r.sortOrder(), r.active(), r.startsAt(), r.endsAt(), r.createdAt());
    }
}
