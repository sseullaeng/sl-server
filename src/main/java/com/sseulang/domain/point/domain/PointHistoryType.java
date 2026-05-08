package com.sseulang.domain.point.domain;

/**
 * point_histories.point_type ENUM 매핑. V1 init schema 의 한글 ENUM 그대로.
 *
 * <ul>
 *   <li>{@link #충전} — 토스 결제로 잔액 증가 (Day 7)</li>
 *   <li>{@link #결제} — 거래 결제 시 구매자 잔액 차감 (Day 8 — 즉시 정산 정책. 라운드 11 부터는 사용 X)</li>
 *   <li>{@link #판매정산} — 거래 정산 시 판매자 잔액 적립 (Day 8 / 라운드 11 인수확인)</li>
 *   <li>{@link #출금} — 출금 신청 시 잔액 차감 (Day 8)</li>
 *   <li>{@link #환불} — Day 8 거래 취소 시 양쪽 잔액 원복 (라운드 11 부터는 거래환불로 분리)</li>
 *   <li>{@link #배달결제} — 배달 정산 시 요청자 잔액 차감 (Day 9)</li>
 *   <li>{@link #배달정산} — 배달 정산 시 라이더 잔액 적립 (Day 9)</li>
 *   <li>{@link #거래보관} — 라운드 11 예약 시 buyer balance↓ + hold↑ (escrow hold). 프론트 라벨 "거래 #N 보관".</li>
 *   <li>{@link #거래환불} — 라운드 11 거래 취소 시 buyer balance↑ + hold↓. 프론트 라벨 "거래 #N 취소 환불".</li>
 * </ul>
 *
 * <p>라운드 11 인수확인 시 buyer 의 hold 해제는 잔액 변화 X 이므로 history 미적재 (Aggregate 정책 — V16 주석 동기).
 * seller 의 정산 적립은 기존 {@link #판매정산} 활용, 프론트 라벨 "거래 #N 정산 수령".</p>
 */
public enum PointHistoryType {
    충전,
    결제,
    판매정산,
    출금,
    환불,
    배달결제,
    배달정산,
    거래보관,
    거래환불
}
