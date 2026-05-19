package com.sseulang.domain.admin.application;

import com.sseulang.domain.admin.domain.Admin;
import com.sseulang.domain.admin.domain.AdminRepository;
import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtClaims;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLoginServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenStore refreshTokenStore;

    private AdminLoginService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties("0123456789012345678901234567890123", 1800L, 604800L);
        // mock encoder 의 encode("...") 는 기본 null 반환 — dummyPasswordHash 가 null 이라도
        // matches(null) 호출은 실제 verify 대상 (mock 이라 boolean 반환만 봄). 게이트 2 dummy hash
        // 형식 회귀는 별도 테스트 (real_BCrypt_*)에서 검증.
        service = new AdminLoginService(adminRepository, passwordEncoder, jwtProvider, refreshTokenStore, props);
    }

    private Admin existing() {
        Admin a = Admin.create("admin1", "$2a$10$hashed", "관리자");
        ReflectionTestUtils.setField(a, "id", 7L);
        return a;
    }

    @Test
    @DisplayName("login 정상_AT/RT 발급 + RT 저장 (subject=admin.id, role=ADMIN)")
    void login_정상() {
        Admin admin = existing();
        when(adminRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pw", admin.getPassword())).thenReturn(true);
        when(jwtProvider.issueAccessToken(7L, "ADMIN")).thenReturn("at-token");
        when(refreshTokenStore.currentTokenVersion("ADMIN", 7L)).thenReturn(0L);
        when(jwtProvider.issueRefreshToken(7L, "ADMIN", 0L)).thenReturn("rt-token");
        when(jwtProvider.parse("rt-token")).thenReturn(new JwtClaims(7L, "ADMIN", "rt-jti", 0L, null, null));

        TokenPair pair = service.login("admin1", "pw");

        assertThat(pair.accessToken()).isEqualTo("at-token");
        assertThat(pair.refreshToken()).isEqualTo("rt-token");
        verify(refreshTokenStore, times(1)).save(eq("ADMIN"), eq(7L), eq("rt-jti"), any(Duration.class));
    }

    @Test
    @DisplayName("login 잘못된 username_AUTH_LOGIN_FAILED + dummy hash 로 BCrypt 강제 실행 (timing leak 방어)")
    void login_미존재() {
        when(adminRepository.findByUsername("none")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("none", "pw"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        verify(refreshTokenStore, never()).save(any(), any(), any(), any());
        // 핵심: 미존재 케이스에서도 passwordEncoder.matches 가 1회 호출되어야 함
        // (정상 admin 분기와 동일 비용 → username 존재 여부 timing leak 차단).
        verify(passwordEncoder, times(1)).matches(eq("pw"), any());
    }

    @Test
    @DisplayName("login 잘못된 password_AUTH_LOGIN_FAILED")
    void login_password_불일치() {
        Admin admin = existing();
        when(adminRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", admin.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> service.login("admin1", "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        verify(jwtProvider, never()).issueAccessToken(any(), any());
    }

    @Test
    @DisplayName("login 비활성 admin_AUTH_LOGIN_FAILED + BCrypt 강제 실행 (active 여부 timing leak 방어)")
    void login_비활성() {
        Admin admin = Admin.create("admin1", "$2a$10$hashed", "관리자");
        ReflectionTestUtils.setField(admin, "id", 7L);
        ReflectionTestUtils.setField(admin, "active", false);
        when(adminRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.login("admin1", "pw"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        // 핵심: inactive admin 도 active admin 과 동일하게 BCrypt 가 호출되어야 함
        // (이전 단락 평가는 active 여부 timing leak 였음).
        verify(passwordEncoder, times(1)).matches(eq("pw"), eq(admin.getPassword()));
    }

    @Test
    @DisplayName("login null/blank 입력_AUTH_LOGIN_FAILED")
    void login_blank() {
        assertThatThrownBy(() -> service.login(null, "pw"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.login("admin1", ""))
                .isInstanceOf(BusinessException.class);
    }

    // ───────── 게이트 2 회귀: dummy hash 가 진짜 BCrypt 형식 + 실 비교 비용 ─────────

    @Test
    @DisplayName("dummyPasswordHash 가 BCrypt 패턴이고 matches() 가 정상 작동 (게이트 2 회귀: 형식 깨짐 차단)")
    void dummyHash_실제_BCrypt_패턴() {
        BCryptPasswordEncoder real = new BCryptPasswordEncoder();
        JwtProperties props = new JwtProperties("0123456789012345678901234567890123", 1800L, 604800L);
        AdminLoginService realSvc = new AdminLoginService(
                adminRepository, real, jwtProvider, refreshTokenStore, props
        );

        String dummyHash = (String) ReflectionTestUtils.getField(realSvc, "dummyPasswordHash");
        // BCrypt 표준 패턴: $2[ayb]$cost$22salt + 31hash = 총 60자
        assertThat(dummyHash)
                .as("dummy hash 가 BCrypt 표준 형식 — 깨지면 matches 가 cost 안 태우고 즉시 실패해 timing leak 부활")
                .matches("^\\$2[ayb]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
        // sentinel 평문이 정말 이 hash 와 매칭되는지 — 양성 검증
        String sentinel = (String) ReflectionTestUtils.getField(
                AdminLoginService.class, "DUMMY_PASSWORD_PLAINTEXT"
        );
        assertThat(real.matches(sentinel, dummyHash))
                .as("encode 결과가 같은 plaintext 로 다시 matches 통과 — BCrypt 정상 동작")
                .isTrue();
    }

    @Test
    @DisplayName("미존재 username 도 실제 BCryptEncoder 로 비교 비용 태움 (timing 균일 회귀)")
    void login_미존재_BCrypt_실비교() {
        BCryptPasswordEncoder real = new BCryptPasswordEncoder();
        JwtProperties props = new JwtProperties("0123456789012345678901234567890123", 1800L, 604800L);
        when(adminRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
        AdminLoginService realSvc = new AdminLoginService(
                adminRepository, real, jwtProvider, refreshTokenStore, props
        );

        long t0 = System.nanoTime();
        assertThatThrownBy(() -> realSvc.login("nonexistent", "any-pw"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        // BCrypt cost=10 은 현대 HW 에서 ~30~80ms. 머신 분산 고려해 lower bound 만 검증 — flaky 회피.
        // 이 lower bound 가 통과해야 미존재 username 분기에서도 실 BCrypt 비용이 났다는 증거.
        assertThat(elapsedMs)
                .as("미존재 username 분기에서도 실 BCrypt 비용 ≥10ms — dummy hash 형식 깨지면 1ms 이내 실패")
                .isGreaterThanOrEqualTo(10L);
    }
}
