package com.sseulang.domain.admin.application;

import com.sseulang.domain.admin.domain.Admin;
import com.sseulang.domain.admin.domain.AdminRepository;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 관리자 로그인 — username + BCrypt password 검증 → JWT 발급 (subject=admin.id, role=ADMIN).
 *
 * <p>일반 사용자 로그인 ({@link com.sseulang.domain.auth.application.OAuthLoginService}) 와 별도로
 * 관리하지만, JWT 발급 / RefreshTokenStore 인프라는 공유. 관리자가 발급받은 토큰의 subject 는
 * {@code admin.id}, role 은 {@code ADMIN} — admin chain 의 ROLE_ADMIN 가드와 정합.</p>
 *
 * <p>실패 응답은 {@link ErrorCode#AUTH_LOGIN_FAILED} 로 통일 (username 존재 여부 노출 X, CLAUDE.md §4 보안 룰).</p>
 */
@Service
public class AdminLoginService {

    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * username enumeration timing 공격 방어용 sentinel 평문.
     * <p>실제 admin password 와 충돌 가능성을 사실상 0 으로 만들기 위해 긴 random sentinel 사용.
     * 충돌하더라도 admin 측에서 isActive() 체크 + role 차원 격리로 부수 피해 없음.</p>
     */
    private static final String DUMMY_PASSWORD_PLAINTEXT = "__sseulang_dummy_admin_sentinel_v1__";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Duration refreshTokenTtl;
    /**
     * username 미존재 / inactive 분기에서도 동일 비용으로 {@code matches()} 를 강제하기 위한 dummy hash.
     * 생성자에서 주입된 {@link PasswordEncoder} 로 직접 encode — 하드코딩 시 인코더 종류 / 형식 어긋남
     * 위험을 제거하고 항상 유효 형식 보장 (게이트 2 보강). 시작 시 1회 BCrypt 비용 ~50ms.
     */
    private final String dummyPasswordHash;

    public AdminLoginService(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            JwtProperties jwtProperties
    ) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD_PLAINTEXT);
    }

    public TokenPair login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        Admin admin = adminRepository.findByUsername(username).orElse(null);
        // 미존재여도 dummy hash 로 항상 BCrypt 를 돌려 timing 균일화 (단락 평가 X).
        String hashToCompare = (admin != null) ? admin.getPassword() : dummyPasswordHash;
        boolean passwordOk = passwordEncoder.matches(password, hashToCompare);

        if (admin == null || !admin.isActive() || !passwordOk) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }

        String at = jwtProvider.issueAccessToken(admin.getId(), ADMIN_ROLE);
        long tv = refreshTokenStore.currentTokenVersion(ADMIN_ROLE, admin.getId());
        String rt = jwtProvider.issueRefreshToken(admin.getId(), ADMIN_ROLE, tv);
        String rtJti = jwtProvider.parse(rt).jti();
        refreshTokenStore.save(ADMIN_ROLE, admin.getId(), rtJti, refreshTokenTtl);
        return new TokenPair(at, rt);
    }
}
