package com.sseulang.domain.message.application.event;

import com.sseulang.domain.message.application.dto.MessageResult;
import com.sseulang.domain.notification.application.dto.NotificationResult;

/**
 * 채팅 메시지 / 알림 실시간 publish 요청 이벤트 (follow-up #19 — AFTER_COMMIT 분리).
 *
 * <p>MessageApplicationService.send 가 트랜잭션 안에서 발행, listener 가 commit 후 publish.
 * 트랜잭션 롤백 시 listener 호출 X — 메시지 저장 실패 시 STOMP 메시지 발송 안 됨 (정합성).</p>
 */
public record ChatRealtimePublishRequestedEvent(
        Long chatRoomId,
        Long opponentId,
        MessageResult message,
        NotificationResult notification
) {
}
