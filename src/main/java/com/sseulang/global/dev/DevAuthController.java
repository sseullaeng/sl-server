package com.sseulang.global.dev;

import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.security.CookieUtil;
import com.sseulang.global.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 smoke test 용 인증 우회 endpoint. <b>이중 가드</b>:
 * <ol>
 *   <li>{@code @Profile("local")} — local 프로필일 때만 후보 등록</li>
 *   <li>{@code @ConditionalOnProperty} — 명시적 opt-in 플래그 ({@code app.dev-auth.enabled=true}) 필요</li>
 * </ol>
 * 단독 {@code @Profile} 만으로는 {@code prod,local} 처럼 복수 프로필 활성 시 prod 노출 위험이 남아
 * Codex 게이트 1 (2026-04-29) 권고로 명시 플래그 추가.
 *
 * <p>OAuth 콘솔 키 없이도 임의 user 시드 + JWT 쿠키 받아서 Swagger / curl 시나리오 가능.
 * 정식 OAuth 흐름 (가이드 §4.4) 과는 무관 — local 작업 편의 용이며 실서비스 배포 시 등록 X.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/dev")
@Profile("local")
@ConditionalOnProperty(name = "app.dev-auth.enabled", havingValue = "true")
public class DevAuthController {

    private final UserApplicationService userApplicationService;
    private final JwtProvider jwtProvider;
    private final CookieUtil cookieUtil;

    public DevAuthController(
            UserApplicationService userApplicationService,
            JwtProvider jwtProvider,
            CookieUtil cookieUtil
    ) {
        this.userApplicationService = userApplicationService;
        this.jwtProvider = jwtProvider;
        this.cookieUtil = cookieUtil;
    }

    /**
     * 임시 user 를 시드(또는 기존 user 재사용)하고 JWT 쿠키 발급.
     * 동일 nickname 으로 재호출 시 같은 user 반환 (멱등).
     */
    @PostMapping("/seed-and-login")
    public ResponseEntity<ApiResponse<DevLoginResponse>> seedAndLogin(
            HttpServletRequest request,
            @Valid @RequestBody DevLoginRequest req
    ) {
        String socialId = "dev-" + req.nickname();
        // 운영 DB 혼입 시 식별성을 위해 dev+{nickname}@dev.local 네임스페이스 (Codex 게이트 1 권고).
        Email email = new Email("dev+" + req.nickname() + "@dev.local");
        // SocialProvider.DEV 사용 — KAKAO/GOOGLE 의미 오염 차단 (Codex 게이트 1 보강).
        User user = userApplicationService.findOrCreateBySocial(
                SocialProvider.DEV,
                socialId,
                email,
                req.nickname(),
                null
        );
        String role = "USER";
        String at = jwtProvider.issueAccessToken(user.getId(), role);
        String rt = jwtProvider.issueRefreshToken(user.getId(), role);

        // CSRF 토큰을 응답 쿠키로 강제 박기 — Spring Security 6 deferred load 회피.
        // 후속 POST/PATCH 요청에서 X-XSRF-TOKEN 헤더로 echo 가능하도록.
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) {
            csrf.getToken();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.accessTokenCookie(at).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.refreshTokenCookie(rt).toString())
                .body(ApiResponse.ok(new DevLoginResponse(user.getId(), user.getNickname())));
    }

    public record DevLoginRequest(@NotBlank @Size(max = 50) String nickname) { }

    public record DevLoginResponse(Long userId, String nickname) { }
}
