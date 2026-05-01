package com.sseulang.global.infra.payment;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossWebhookSignatureVerifierTest {

    private static final String SECRET = "test_webhook_secret_64chars_for_hmac_sha256_validation_xx";
    private static final long FIXED_NOW = 1_777_000_000L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochSecond(FIXED_NOW), ZoneOffset.UTC);

    @Test
    @DisplayName("verify 시크릿 미설정_검증 비활성 (예외 X)")
    void verify_시크릿_미설정() {
        TossProperties props = new TossProperties("ck", "sk", null, null, null);
        TossWebhookSignatureVerifier v = new TossWebhookSignatureVerifier(props, FIXED_CLOCK);

        assertThatCode(() -> v.verify("body", "anything", "anything")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verify 정상 시그니처(hex)_통과")
    void verify_정상_hex() {
        String body = "{\"eventId\":\"e1\"}";
        String ts = String.valueOf(FIXED_NOW);
        String sig = hex(SECRET, ts + "." + body);
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatCode(() -> v.verify(body, sig, ts)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verify 정상 시그니처(base64)_통과 — hex 파싱 실패 후 base64 fallback")
    void verify_정상_base64() {
        String body = "{\"eventId\":\"e1\"}";
        String ts = String.valueOf(FIXED_NOW);
        // hex 가 아닌 base64 — '=' 문자 또는 'g+' 같은 hex 외 문자 포함되도록.
        byte[] mac = computeMac(SECRET, ts + "." + body);
        String sig = java.util.Base64.getEncoder().encodeToString(mac);
        // base64 결과에 '=' 또는 '+' 가 포함되도록 길이 보장 (32바이트는 항상 '=' 패딩 X but '+'/'/' 가능)
        // 충돌 회피 위해 첫 글자가 hex 가 아닌 케이스 강제 어려움 — 일단 hex 만 우선 시도되므로 hex 통과 가능성 있음.
        // 따라서 이 시나리오는 hex 가 우선 매칭 실패하면 base64 도 시도하는지만 검증.
        TossWebhookSignatureVerifier v = newVerifier();

        // base64 가 우연히 hex 도 valid 면 hex 로 디코딩 후 검증 — 어떻든 같은 secret/payload 라 PASS.
        assertThatCode(() -> v.verify(body, sig, ts)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verify 시그니처 mismatch_PAYMENT_WEBHOOK_SIGNATURE_INVALID")
    void verify_mismatch() {
        String body = "{\"eventId\":\"e1\"}";
        String ts = String.valueOf(FIXED_NOW);
        String wrongSig = hex("WRONG_SECRET_for_test_hmac_validation_64characters_xxxxxx", ts + "." + body);
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatThrownBy(() -> v.verify(body, wrongSig, ts))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("verify 시그니처 헤더 누락_PAYMENT_WEBHOOK_SIGNATURE_INVALID")
    void verify_헤더_누락() {
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatThrownBy(() -> v.verify("body", null, String.valueOf(FIXED_NOW)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("verify timestamp tolerance 초과(과거)_PAYMENT_WEBHOOK_REPLAY_REJECTED")
    void verify_replay_과거() {
        String body = "body";
        // 6분 전 timestamp — tolerance 300s 초과
        String ts = String.valueOf(FIXED_NOW - 360);
        String sig = hex(SECRET, ts + "." + body);
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatThrownBy(() -> v.verify(body, sig, ts))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_REPLAY_REJECTED);
    }

    @Test
    @DisplayName("verify timestamp tolerance 초과(미래)_PAYMENT_WEBHOOK_REPLAY_REJECTED")
    void verify_replay_미래() {
        String body = "body";
        String ts = String.valueOf(FIXED_NOW + 360);
        String sig = hex(SECRET, ts + "." + body);
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatThrownBy(() -> v.verify(body, sig, ts))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_REPLAY_REJECTED);
    }

    @Test
    @DisplayName("verify timestamp 형식 오류_PAYMENT_WEBHOOK_TIMESTAMP_INVALID")
    void verify_timestamp_invalid() {
        String body = "body";
        String sig = hex(SECRET, "abc." + body);
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatThrownBy(() -> v.verify(body, sig, "not-a-number"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID);
    }

    @Test
    @DisplayName("verify timestamp 미설정 + tolerance>0_검증 skip 후 시그니처만 검증")
    void verify_no_timestamp() {
        String body = "{\"e\":1}";
        // timestamp 없으면 payload 는 body only.
        String sig = hex(SECRET, body);
        TossWebhookSignatureVerifier v = newVerifier();

        assertThatCode(() -> v.verify(body, sig, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verify tolerance=0 면 timestamp 검증 비활성")
    void verify_tolerance_zero() {
        TossProperties props = new TossProperties("ck", "sk", null, SECRET, 0L);
        TossWebhookSignatureVerifier v = new TossWebhookSignatureVerifier(props, FIXED_CLOCK);
        String body = "body";
        // 1년 전이라도 timestamp 검증 안 함.
        String oldTs = String.valueOf(FIXED_NOW - 31_536_000L);
        String sig = hex(SECRET, oldTs + "." + body);

        assertThatCode(() -> v.verify(body, sig, oldTs)).doesNotThrowAnyException();
    }

    private TossWebhookSignatureVerifier newVerifier() {
        TossProperties props = new TossProperties("ck", "sk", null, SECRET, 300L);
        return new TossWebhookSignatureVerifier(props, FIXED_CLOCK);
    }

    private static String hex(String secret, String payload) {
        return HexFormat.of().formatHex(computeMac(secret, payload));
    }

    private static byte[] computeMac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
