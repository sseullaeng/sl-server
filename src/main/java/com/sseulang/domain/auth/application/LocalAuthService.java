package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * LOCAL (email + password) 가입/로그인 흐름. OAuth 와 분리된 별도 service.
 *
 * <p>보안 정책:
 * <ul>
 *   <li>가입: BCrypt 해싱 후 저장. 사전 email 중복 체크 + UNIQUE race 패배자 보정.</li>
 *   <li>로그인: 미존재 user / 차단 / 비밀번호 불일치 모두 {@link ErrorCode#AUTH_LOGIN_FAILED} 통합 응답
 *       (email 존재 여부 leak 방지). dummy hash 로 timing 균일화 (미존재 user 분기에서도 BCrypt 비용
 *       태움 — username enumeration timing 공격 방어, AdminLogin 패턴과 동일).</li>
 *   <li>비밀번호 정책: 8자 이상, 72자 이하 (BCrypt 72-byte limit).</li>
 * </ul>
 *
 * <p>가입 직후 자동 JWT 발급 — 별도 로그인 호출 없이 즉시 인증된 세션. OAuth 흐름과 동일.</p>
 */
@Service
public class LocalAuthService {

    private static final String DEFAULT_ROLE = "USER";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    /**
     * username enumeration timing 공격 방어용 sentinel. 미존재 user 분기에서도 BCrypt cost 를
     * 강제 태우기 위해 항상 호출. AdminLoginService 와 동일 패턴.
     */
    private static final String DUMMY_PASSWORD_PLAINTEXT = "__sseulang_dummy_user_sentinel_v1__";

    private final UserRepository userRepository;
    private final com.sseulang.domain.user.application.UserApplicationService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final EmailVerificationService verificationService;
    private final Duration refreshTokenTtl;
    /**
     * 생성자 시점에 실제 인코더로 encode — 하드코딩 시 인코더 변경/형식 깨짐 위험 제거 (게이트 2 보강).
     */
    private final String dummyPasswordHash;

    public LocalAuthService(
            UserRepository userRepository,
            com.sseulang.domain.user.application.UserApplicationService userService,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            EmailVerificationService verificationService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.verificationService = verificationService;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD_PLAINTEXT);
    }

    /**
     * LOCAL 가입 + 자동 로그인. email 중복 시 {@link ErrorCode#USER_EMAIL_DUPLICATED} (다른 provider
     * 로 이미 가입돼있어도 동일 — 가이드 §12 정책).
     *
     * <p>{@code @Transactional} — user 저장 + 인증 토큰 발급을 한 트랜잭션으로 묶어 atomicity 보장
     * (follow-up #41). 메일 발송은 트랜잭션 commit 후 별도 listener 가 처리 — 메일 다운 시에도
     * user/token 보존, 재발송으로 회복 가능.</p>
     */
    @Transactional
    public TokenPair signup(Email email, String rawPassword, String nickname) {
        validatePassword(rawPassword);

        // 사전 중복 체크 (fast path) — UNIQUE race 의 1차 가드. 마지막 가드는 DB UNIQUE.
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
        });

        String hashed = passwordEncoder.encode(rawPassword);
        User saved;
        try {
            saved = userRepository.save(User.createLocalUser(email, hashed, nickname));
        } catch (DataIntegrityViolationException race) {
            // UNIQUE race 패배 — 다른 트랜잭션이 같은 email 로 먼저 가입. 명시 거부 (다른 제약 위반은 원본 throw).
            if (userRepository.findByEmail(email).isPresent()) {
                throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
            }
            throw race;
        }
        // 가입 직후 인증 메일 발송. 사용자는 verified=false 상태로 자동 로그인되며, 민감 기능은
        // verified=true 가 될 때까지 차단 (UserApplicationService.requireVerified).
        verificationService.issueSignupToken(saved.getId(), saved.getEmail());
        return issueTokens(saved);
    }

    /**
     * LOCAL 로그인. 미존재 user / 비밀번호 불일치 / 차단·삭제 계정 모두 동일 응답으로 통합 —
     * email 존재 여부 / 계정 상태 leak 차단. dummy hash 로 BCrypt timing 균일.
     */
    public TokenPair login(Email email, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        // BCrypt 72-byte 초과 입력은 truncation 위험 — login 도 byte 가드 (signup 과 동일).
        // 실패는 LOGIN_FAILED 통합 (이메일 존재 여부 leak 방지, AdminLogin 패턴).
        if (rawPassword.length() > MAX_PASSWORD_LENGTH
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        // 미존재 / 비번 미보유(소셜 가입자) 계정에서도 dummy hash 로 BCrypt cost 강제.
        String hashToCompare = (user != null && user.hasPassword()) ? user.getPassword() : dummyPasswordHash;
        boolean passwordOk = passwordEncoder.matches(rawPassword, hashToCompare);

        // SUSPENDED (시한부 정지) 도 LOGIN_FAILED 로 통합 — 정지 사실 leak 방지 + 기존 패스워드로 우회 차단
        // (Codex round 9 hotfix). isAccessibleAt 은 blocked/deleted/suspended 통합 가드.
        boolean accessible = user != null && user.isAccessibleAt(java.time.LocalDateTime.now());
        if (user == null || !user.hasPassword() || !accessible || !passwordOk) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        // 휴면 판정 기준 — 응답 status 가 ACTIVE 로 자동 복귀.
        userService.recordLogin(user.getId());
        return issueTokens(user);
    }

    /**
     * 비밀번호 길이 검증. BCrypt 는 평문을 UTF-8 인코딩한 byte 길이 72 까지만 처리하고 그 이상은
     * 자동 truncation 됨 — char-length 만 보면 한글 등 multi-byte 문자가 통과되어 보안 약화 (게이트 1).
     * char + UTF-8 byte 둘 다 검증.
     */
    private void validatePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_INVALID);
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_INVALID);
        }
    }

    private TokenPair issueTokens(User user) {
        String at = jwtProvider.issueAccessToken(user.getId(), DEFAULT_ROLE);
        long tv = refreshTokenStore.currentTokenVersion(DEFAULT_ROLE, user.getId());
        String rt = jwtProvider.issueRefreshToken(user.getId(), DEFAULT_ROLE, tv);
        String rtJti = jwtProvider.parse(rt).jti();
        refreshTokenStore.save(DEFAULT_ROLE, user.getId(), rtJti, refreshTokenTtl);
        return new TokenPair(at, rt);
    }
}
