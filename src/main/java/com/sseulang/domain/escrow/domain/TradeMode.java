package com.sseulang.domain.escrow.domain;

/**
 * 거래 모드. 결정 #4 — D-Hybrid.
 *
 * <ul>
 *   <li>{@code INTERNAL}: 쓸랭 내부 거래. itemPrice 까지 escrow hold + buyer 수령 확인 후 정산.</li>
 *   <li>{@code EXTERNAL}: 외부 플랫폼 거래. itemPrice 처리 X — 배달비/수수료만.</li>
 * </ul>
 */
public enum TradeMode {
    INTERNAL,
    EXTERNAL;

    public boolean isInternal() {
        return this == INTERNAL;
    }
}
