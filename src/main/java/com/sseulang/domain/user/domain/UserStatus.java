package com.sseulang.domain.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Admin 응답에서 derive 되는 회원 상태. DB 컬럼 X — {@link User#derivedStatus} 가 시점마다 계산.
 *
 * <p>우선순위: {@link #WITHDRAWN} &gt; {@link #SUSPENDED} &gt; {@link #DORMANT} &gt; {@link #ACTIVE}.</p>
 *
 * <ul>
 *   <li>{@link #ACTIVE} — 정상</li>
 *   <li>{@link #SUSPENDED} — 시한부 활동정지 중 (suspended_at + suspend_days 만료 전)</li>
 *   <li>{@link #WITHDRAWN} — 탈퇴 (is_deleted=true)</li>
 *   <li>{@link #DORMANT} — 휴면 (90일 이상 미접속)</li>
 * </ul>
 */
@Schema(description = "회원 상태 (derived) — ACTIVE / SUSPENDED / WITHDRAWN / DORMANT")
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN,
    DORMANT
}
