package com.sseulang.domain.notification.domain;

/**
 * 알림 대분류 (PR-F #5 라운드 12). 프론트가 알림 목록을 시스템/신고/문의/사용자 4가지 카테고리로 분류 노출.
 *
 * <ul>
 *   <li>{@link #SYSTEM} — 관리자 공지/이벤트 broadcast</li>
 *   <li>{@link #REPORT} — 신고 처리 결과</li>
 *   <li>{@link #INQUIRY} — 1:1 문의 답변</li>
 *   <li>{@link #USER} — 일반 사용자 액션 (메시지/거래/결제/정산 등) — default</li>
 * </ul>
 *
 * <p>기존 NotificationType (메시지/거래/결제/...) 와 별도 — type 은 구체 이벤트, category 는 분류 묶음.</p>
 */
public enum NotificationCategory {
    SYSTEM,
    REPORT,
    INQUIRY,
    USER
}
