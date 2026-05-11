package com.sseulang.domain.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private WebhookEventSource source;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static WebhookEvent received(
            WebhookEventSource source,
            String eventId,
            String eventType,
            String paymentKey,
            String rawPayload,
            LocalDateTime now
    ) {
        if (source == null) {
            throw new IllegalArgumentException("source 는 필수입니다");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 는 필수입니다");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType 은 필수입니다");
        }
        if (rawPayload == null) {
            throw new IllegalArgumentException("rawPayload 는 필수입니다");
        }
        if (now == null) {
            throw new IllegalArgumentException("now 는 필수입니다");
        }
        WebhookEvent e = new WebhookEvent();
        e.source = source;
        e.eventId = eventId;
        e.eventType = eventType;
        e.paymentKey = paymentKey;
        e.rawPayload = rawPayload;
        e.receivedAt = now;
        e.createdAt = now;
        return e;
    }

    public void markProcessed(LocalDateTime now) {
        this.processedAt = now;
    }
}
