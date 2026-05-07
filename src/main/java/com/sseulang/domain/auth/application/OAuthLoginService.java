package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.exception.ExternalApiException;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소셜 로그인 흐름 — provider 검증 → user 조회/생성 → JWT 발급 + RT 저장.
 *
 * <p>기본 role 은 "USER". 별도로 {@code app.admin.user-emails} 화이트리스트에 매치되는 email 의
 * OAuth user 는 role="ADMIN" 으로 JWT 발급 — 본인 SSO 로 admin 페이지 접근 가능. admin 페이지의
 * 별도 username/password 로그인 ({@code admins} 테이블) 흐름과 병존.</p>
 *
 * <p><b>권한 회수 운영 절차 (게이트 1 W-1)</b>: allowlist 에서 email 제거만으로는 이미 발급된
 * AT(30분) / RT(7일) 가 즉시 무효화되지 않는다 — 발급 시점 기준이라 그 후 refresh 도 ADMIN role 로
 * rotate 된다. 운영자가 즉시 권한 회수가 필요하면:
 * <ol>
 *   <li>{@code ADMIN_USER_EMAILS} 환경변수에서 해당 email 제거 + 재배포 (이후 신규 로그인은 USER)</li>
 *   <li>{@code RefreshTokenStore.revokeAll("ADMIN", userId)} 호출 — RT 무효화 (Redis tv INCR)</li>
 *   <li>해당 사용자에게 강제 logout 안내 — AT 30분 만료까지는 잔존</li>
 * </ol>
 * R1 follow-up: 운영자 일괄 revoke endpoint (admin 측에서 호출) 추가 검토.</p>
 *
 * <p>클래스 단위 {@code @Transactional} 의도적으로 미적용 — 가이드 §3.10 "외부 API 호출은 트랜잭션
 * 밖에서". UserApplicationService 가 자체 트랜잭션을 가지며, RefreshTokenStore.save 는 Redis 라
 * JPA 트랜잭션 밖. AT 발급은 stateless.</p>
 */
@Slf4j
@Service
public class OAuthLoginService {

    private static final String DEFAULT_ROLE = "USER";
    private static final String ADMIN_ROLE = "ADMIN";

    private final Map<SocialProvider, OAuthProvider> providersByType;
    private final UserApplicationService userService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Duration refreshTokenTtl;

    /**
     * 본인 OAuth 로그인 시 ROLE_ADMIN JWT 를 발급할 email 화이트리스트 (lowercase 정규화).
     * 빈 set 이면 모두 USER 로 발급. {@code app.admin.user-emails} (콤마구분) 에서 주입.
     */
    private final Set<String> adminUserEmails;

    public OAuthLoginService(
            List<OAuthProvider> providers,
            UserApplicationService userService,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            JwtProperties jwtProperties,
            @Value("${app.admin.user-emails:}") String adminUserEmailsRaw
    ) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(OAuthProvider::supports, Function.identity()));
        this.userService = userService;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
        this.adminUserEmails = parseAdminEmails(adminUserEmailsRaw);
        if (!adminUserEmails.isEmpty()) {
            log.warn("[admin-oauth] OAuth role=ADMIN 발급 대상 email {}건 활성 — app.admin.user-emails. "
                    + "운영 진입 전 의도된 대상인지 점검.", adminUserEmails.size());
        }
    }

    private static Set<String> parseAdminEmails(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public TokenPair loginWithCode(SocialProvider provider, String code, String redirectUri) {
        OAuthProvider impl = providersByType.get(provider);
        if (impl == null) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        OAuthUserInfo info;
        try {
            info = impl.exchangeCodeAndFetch(code, redirectUri);
        } catch (ExternalApiException e) {
            // 외부 인프라 예외 → 사용자 응답은 AUTH_OAUTH_FAILED 통일.
            // 원인은 cause 에 보존 + 운영용 로그.
            log.warn("OAuth provider {} 호출 실패: {}", provider, e.getExternalServiceName(), e);
            throw new BusinessException(ErrorCode.AUTH_OAUTH_FAILED);
        }

        User user = userService.findOrCreateBySocial(
                info.provider(),
                info.providerId(),
                info.email(),
                info.nickname(),
                info.profileImage()
        );

        // SUSPENDED (시한부 정지) 도 USER_BLOCKED 통합 — Codex round 9 hotfix.
        if (!user.isAccessibleAt(java.time.LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }

        // 휴면 판정 기준 — 응답 status 가 ACTIVE 로 자동 복귀.
        userService.recordLogin(user.getId());

        // role 결정 — email allowlist 매치 시 ADMIN, 아니면 기본 USER.
        // RefreshTokenStore 의 (role,userId) 격리 — 같은 userId 가 USER/ADMIN 양쪽 RT 보유 가능하지만
        // 일반적으로 마지막 로그인 흐름의 role 만 사용 (logout-rotate 가 cleanup).
        String role = isAdminEmail(user.getEmail()) ? ADMIN_ROLE : DEFAULT_ROLE;
        if (ADMIN_ROLE.equals(role)) {
            log.warn("[admin-oauth][PROMOTE] OAuth user.id={} email={} → role=ADMIN JWT 발급",
                    user.getId(), user.getEmail());
        }

        String at = jwtProvider.issueAccessToken(user.getId(), role);
        // tv: store 의 현재 버전을 RT claim 에 박는다. 이후 revokeAll → INCR 시 본 RT 는 mismatch 로 거부됨.
        long tv = refreshTokenStore.currentTokenVersion(role, user.getId());
        String rt = jwtProvider.issueRefreshToken(user.getId(), role, tv);
        String rtJti = jwtProvider.parse(rt).jti();
        refreshTokenStore.save(role, user.getId(), rtJti, refreshTokenTtl);

        return new TokenPair(at, rt);
    }

    private boolean isAdminEmail(String email) {
        if (email == null || adminUserEmails.isEmpty()) return false;
        return adminUserEmails.contains(email.toLowerCase(java.util.Locale.ROOT));
    }
}
