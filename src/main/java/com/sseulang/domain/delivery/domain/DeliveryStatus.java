package com.sseulang.domain.delivery.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 배달대행 요청 상태. DB VARCHAR(20) ENUM 매핑.
 *
 * <pre>
 *   모집중 → 수락 → 배송중 → 배송완료 → 정산완료
 *      └→ 취소 (요청자, 모집중 한정 — 5/6 이전 단순화)
 * </pre>
 *
 * <p>수락 단계에서 race 가 있으므로 ApplicationService 가 conditional UPDATE
 * ({@code WHERE id = ? AND status = '모집중'}) 로 동시 수락 차단. 본 enum 은 단일 row
 * 의 다음 전이가 가능한지 표현만 한다.</p>
 */
@Schema(description = "배달 상태. 모집중→수락→배송중→배송완료→정산완료 (또는 취소).")
public enum DeliveryStatus {
    모집중,
    수락,
    배송중,
    배송완료,
    정산완료,
    취소;

    public boolean isTerminal() {
        return this == 정산완료 || this == 취소;
    }

    /** 라이더가 수락 가능한 상태 (모집중 → 수락). */
    public boolean canAccept() {
        return this == 모집중;
    }

    /** 라이더가 픽업 처리 가능한 상태 (수락 → 배송중). */
    public boolean canPickup() {
        return this == 수락;
    }

    /** 라이더가 배송 완료 처리 가능한 상태 (배송중 → 배송완료). */
    public boolean canDeliver() {
        return this == 배송중;
    }

    /** 요청자가 정산 확인 가능한 상태 (배송완료 → 정산완료). 포인트 이동은 호출자 책임. */
    public boolean canSettle() {
        return this == 배송완료;
    }

    /**
     * 요청자가 취소 가능한 상태 (모집중 한정). 수락 이후 취소는 5/6 이후 분쟁 정책 도입 시 확장.
     */
    public boolean canRequesterCancel() {
        return this == 모집중;
    }

    /**
     * 라이더 좌표 publish / 참여자 위치 조회가 의미 있는 상태.
     * <ul>
     *   <li>{@code 수락}: 라이더가 픽업 장소 이동 중</li>
     *   <li>{@code 배송중}: 픽업 후 도착지 이동 중</li>
     * </ul>
     * 모집중(아직 라이더 없음) / 배송완료 / 정산완료 / 취소(종료) 는 false — 위치 추적 의미 없음 +
     * 종료 후 위치 publish/조회 차단으로 개인정보 잔류 방지 (Codex 게이트 2 W1).
     */
    public boolean canTrackLocation() {
        return this == 수락 || this == 배송중;
    }
}
