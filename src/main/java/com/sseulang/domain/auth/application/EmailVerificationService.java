package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.event.EmailDispatchRequestedEvent;
import com.sseulang.domain.auth.domain.EmailSender;
import com.sseulang.domain.auth.domain.EmailVerification;
import com.sseulang.domain.auth.domain.EmailVerificationRepository;
import com.sseulang.domain.auth.domain.VerificationPurpose;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이메일 인증 토큰 발급 + 검증.
 *
 * <p>흐름:
 * <ol>
 *   <li>{@link #issueSignupToken} — 가입 직후 또는 재발송. 토큰 생성 → DB 저장 → 메일 발송.</li>
 *   <li>{@link #verifyToken} — 사용자가 메일 링크 클릭. 토큰 매칭 → 만료/재사용 가드 → user.markEmailVerified.</li>
 * </ol>
 *
 * <p>토큰: 32자 UUID (충돌/brute-force 안전). 만료: 24시간. 새 토큰 발급 시 같은 user 의 기존
 * 미사용 SIGNUP 토큰 모두 invalidate — 메일 폭탄 / 토큰 누적 차단 (게이트 1 round 2).
 * 재발송은 60초 cooldown.</p>
 */
@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    /** 동일 user 의 인증 메일 재발송 최소 간격 — 메일 폭탄 방어 (게이트 1 round 2). */
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    /**
     * userId → 마지막 발송 시각. in-memory 단일 인스턴스 가정. 다중 인스턴스 배포 시 Redis-backed
     * counter 로 교체 권장 (5/6 이후 후속). prod 1대 운영 기준 충분.
     */
    private final Map<Long, LocalDateTime> lastIssuedAt = new ConcurrentHashMap<>();

    private final EmailVerificationRepository verificationRepository;
    private final UserApplicationService userService;
    private final EmailSender emailSender;  // 직접 호출은 deprecated — 이벤트 listener 가 발송 (follow-up #41)
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String verificationUrlBase;

    public EmailVerificationService(
            EmailVerificationRepository verificationRepository,
            UserApplicationService userService,
            EmailSender emailSender,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.email.verification-url-base:http://localhost:8080/api/v1/auth/verify-email}")
            String verificationUrlBase
    ) {
        this.verificationRepository = verificationRepository;
        this.userService = userService;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.verificationUrlBase = verificationUrlBase;
    }

    /**
     * SIGNUP 토큰 발급 + 메일 발송. 호출 시:
     * <ol>
     *   <li>해당 user 의 기존 미사용 SIGNUP 토큰 모두 무효화 (구 토큰 누적 차단).</li>
     *   <li>새 토큰 생성/저장/메일 발송.</li>
     *   <li>userId 별 마지막 발송 시각 기록 — 재발송 cooldown 가드 기준.</li>
     * </ol>
     * 가입 직후 흐름 (LocalAuthService.signup) 에서도 호출되며, 가입 시점엔 기존 토큰이 없어 invalidate 는 no-op.
     */
    /**
     * SIGNUP 토큰 발급 + 메일 발송 이벤트 발행 (follow-up #41 atomicity).
     * 메일 발송은 트랜잭션 commit 후 별도 listener 가 처리 — 메일 시스템 다운 시에도
     * user/token 은 보존, 사용자가 {@code /resend-verification} 으로 재시도 가능.
     */
    @Transactional
    public void issueSignupToken(Long userId, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        verificationRepository.invalidateUnusedSignupTokens(userId, now);
        String token = generateToken();
        LocalDateTime expiresAt = now.plus(TOKEN_TTL);
        verificationRepository.save(EmailVerification.issue(userId, token, VerificationPurpose.SIGNUP, expiresAt));
        // AFTER_COMMIT 으로 발송 — 본 트랜잭션 롤백 시 메일 발송 X.
        eventPublisher.publishEvent(new EmailDispatchRequestedEvent(email, verificationUrlBase + "?token=" + token));
        lastIssuedAt.put(userId, now);
    }

    /**
     * 토큰 검증 + user.markEmailVerified. 만료/재사용/없음 모두 명시 ErrorCode.
     *
     * <p>{@link EmailVerificationRepository#markUsedIfValid} atomic UPDATE 로 동시 검증 race 차단
     * (follow-up #42). 정확히 1건만 1 rows affected — 다른 요청은 0 rows → 사유 분기.</p>
     */
    @Transactional
    public void verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int affected = verificationRepository.markUsedIfValid(token, now);
        if (affected == 0) {
            // 사유 분기 — 미존재 / 이미 사용 / 만료 중 정확한 ErrorCode 결정.
            EmailVerification ver = verificationRepository.findByToken(token)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID));
            if (ver.isUsed()) {
                throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
            }
            if (ver.isExpired(now)) {
                throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_EXPIRED);
            }
            // 정상적으론 도달 불가 — race 우승자 트랜잭션이 아직 commit 전 인 보기 드문 경우.
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
        }
        // atomic UPDATE 성공. user 조회 + verified 플래그 마킹.
        EmailVerification ver = verificationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID));
        User user = userService.getById(ver.getUserId());
        user.markEmailVerified();
    }

    /**
     * 인증 메일 재발송 — 본인 user 만 호출. 이미 verified 면 no-op (멱등).
     * 직전 발송 후 {@link #RESEND_COOLDOWN} 이내 재호출은 {@code AUTH_VERIFICATION_RESEND_TOO_SOON} 거부 —
     * 메일 폭탄 방어 (게이트 1 round 2).
     */
    @Transactional
    public void resendForUser(Long userId) {
        User user = userService.getById(userId);
        if (user.isEmailVerified()) {
            return;  // 이미 인증된 사용자 — 토큰 발급/메일 발송 모두 skip
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime last = lastIssuedAt.get(userId);
        if (last != null && Duration.between(last, now).compareTo(RESEND_COOLDOWN) < 0) {
            throw new com.sseulang.global.exception.BusinessException(
                    com.sseulang.global.exception.ErrorCode.AUTH_VERIFICATION_RESEND_TOO_SOON);
        }
        issueSignupToken(user.getId(), user.getEmail());
    }

    private static String generateToken() {
        // 32자 UUID hex (대시 제거) — brute-force 안전한 충분한 entropy.
        return UUID.randomUUID().toString().replace("-", "");
    }
}
