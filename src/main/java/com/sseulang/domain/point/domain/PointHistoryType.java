package com.sseulang.domain.point.domain;

/**
 * point_histories.point_type ENUM 매핑. V1 init schema 의 한글 ENUM 그대로.
 *
 * <ul>
 *   <li>{@link #충전} — 토스 결제로 잔액 증가 (Day 7)</li>
 *   <li>{@link #결제} — 거래 결제 시 구매자 잔액 차감 (Day 8)</li>
 *   <li>{@link #판매정산} — 거래 결제 시 판매자 잔액 적립 (Day 8)</li>
 *   <li>{@link #출금} — 출금 신청 시 잔액 차감 (Day 8)</li>
 *   <li>{@link #환불} — 거래 취소 시 양쪽 잔액 원복 (Day 8)</li>
 * </ul>
 */
public enum PointHistoryType {
    충전,
    결제,
    판매정산,
    출금,
    환불
}
