package com.sseulang.global.infra.payment;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 토스 webhook HMAC-SHA256 시그니처 + replay window 검증.
 *
 * <p>HMAC 입력: {@code timestamp + "." + rawBody} — Stripe 패턴과 동일한 구조 (토스 공식 포맷이
 * 변경될 경우 본 구현만 갱신). secret 미설정 시 검증 비활성 — local/dev 우회용.</p>
 *
 * <p>Comparison 은 {@link MessageDigest#isEqual} 로 timing-safe.</p>
 */
@Component
public class TossWebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(TossWebhookSignatureVerifier.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final TossProperties tossProperties;
    private final Clock clock;

    @Autowired
    public TossWebhookSignatureVerifier(TossProperties tossProperties) {
        this(tossProperties, Clock.systemUTC());
    }

    /** 테스트용 — Clock 주입. Spring 은 본 생성자 사용 X (위 @Autowired 가 우선). */
    public TossWebhookSignatureVerifier(TossProperties tossProperties, Clock clock) {
        this.tossProperties = tossProperties;
        this.clock = clock;
    }

    /**
     * 시그니처 + timestamp 검증. 실패 시 {@link BusinessException} throw.
     *
     * @param rawBody       webhook 원본 body
     * @param signatureHeader 서버에 도달한 시그니처 (hex 또는 base64 — 본 구현은 양쪽 시도)
     * @param timestampHeader epoch seconds (옵션, 없으면 replay 검증 skip)
     */
    public void verify(String rawBody, String signatureHeader, String timestampHeader) {
        String secret = tossProperties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            // local/dev 우회 — prod 는 application-prod.yml 에서 반드시 주입.
            log.warn("[toss-webhook] webhookSecret 미설정 — 시그니처 검증 비활성 (prod 환경에서는 반드시 설정)");
            return;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
        verifyTimestamp(timestampHeader);

        String payload = (timestampHeader == null || timestampHeader.isBlank())
                ? rawBody
                : timestampHeader + "." + rawBody;
        byte[] computed = hmacSha256(secret, payload);
        byte[] received = decodeSignature(signatureHeader);

        if (!MessageDigest.isEqual(computed, received)) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private void verifyTimestamp(String timestampHeader) {
        long tolerance = tossProperties.webhookReplayToleranceSeconds();
        if (tolerance <= 0 || timestampHeader == null || timestampHeader.isBlank()) {
            return;
        }
        long ts;
        try {
            ts = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID);
        }
        long nowSec = Instant.now(clock).getEpochSecond();
        if (Math.abs(nowSec - ts) > tolerance) {
            throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_REPLAY_REJECTED);
        }
    }

    private static byte[] hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // HMAC 알고리즘 자체는 JDK 표준 — 실패 시 환경 문제. 5xx 로 escalate.
            throw new IllegalStateException("HMAC-SHA256 계산 실패", e);
        }
    }

    /** hex 우선, 실패 시 base64 — 토스 콘솔 인코딩 변경 대비. */
    private static byte[] decodeSignature(String signature) {
        String trimmed = signature.trim();
        try {
            return HexFormat.of().parseHex(trimmed);
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getDecoder().decode(trimmed);
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
            }
        }
    }
}
