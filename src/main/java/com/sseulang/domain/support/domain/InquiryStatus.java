package com.sseulang.domain.support.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 1:1 문의 처리 상태.
 *
 * <ul>
 *   <li>{@link #PENDING} — 사용자가 작성, 관리자 미확인. 사용자가 본인 삭제 가능한 유일한 상태.</li>
 *   <li>{@link #PROCESSING} — 관리자가 확인했으나 답변 미완료.</li>
 *   <li>{@link #DONE} — 관리자 답변 완료 (admin_reply + replied_at 채워짐).</li>
 * </ul>
 *
 * <p>전이 룰: {@code PENDING → PROCESSING → DONE} 단방향. 되돌리는 것 ({@code DONE → PENDING}) 은
 * 명세상 유스케이스 없음 — 관리자가 status 변경하더라도 enum 검증으로 순방향만.</p>
 *
 * <p>FAQ/QNA 게시글은 status 가 없음 — 작성 즉시 노출.</p>
 */
@Schema(description = "1:1 문의 처리 상태 — PENDING(대기) / PROCESSING(처리중) / DONE(완료)")
public enum InquiryStatus {
    PENDING,
    PROCESSING,
    DONE
}
