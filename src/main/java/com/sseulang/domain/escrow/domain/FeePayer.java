package com.sseulang.domain.escrow.domain;

/**
 * 수수료 부담 정책. 결정 #5 — feePayer=both 는 양쪽 별도 결제 (G2).
 *
 * <ul>
 *   <li>{@code buyer}  : buyer 가 100% 결제</li>
 *   <li>{@code seller} : seller 가 100% 결제</li>
 *   <li>{@code both}   : 50/50 양쪽 별도 결제</li>
 * </ul>
 */
public enum FeePayer {
    buyer,
    seller,
    both
}
