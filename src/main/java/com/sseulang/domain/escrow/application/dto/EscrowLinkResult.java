package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.EscrowLink;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;

import java.time.LocalDateTime;

/**
 * Escrow link 응답.
 *
 * <p>비로그인 GET 응답 시 노출 필드 최소화 (게이트 1 Nit) — 토큰 leak 시 메타데이터 leak 면적 축소.
 * 신청자 본인의 link 생성 응답은 동일 형식이지만 본인 정보라 leak 위험 X.</p>
 *
 * <p>의도적 노출:
 * <ul>
 *   <li>linkToken — 응답으로 받기 위해 필요</li>
 *   <li>initiatorNickname — 수신자가 누가 보낸 건지 식별</li>
 *   <li>initiatorRole / feePayer / tradeMode — 수신자가 본인 부담 결정</li>
 *   <li>expiresAt — 클라가 만료 시각 표시</li>
 * </ul>
 *
 * <p>의도적 비노출 (Nit fix): id, initiatorId, status, createdAt — public 진입 시 leak 면적 축소.</p>
 */
public record EscrowLinkResult(
        String linkToken,
        String initiatorNickname,
        InitiatorRole initiatorRole,
        FeePayer feePayer,
        TradeMode tradeMode,
        LocalDateTime expiresAt
) {
    public static EscrowLinkResult from(EscrowLink link, String initiatorNickname) {
        return new EscrowLinkResult(
                link.getLinkToken(),
                initiatorNickname,
                link.getInitiatorRole(),
                link.getFeePayer(),
                link.getTradeMode(),
                link.getExpiresAt()
        );
    }
}
