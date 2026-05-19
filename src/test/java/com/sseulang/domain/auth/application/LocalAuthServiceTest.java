package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalAuthServiceTest {

    private static final Email EMAIL = new Email("local@test.com");

    @Mock private UserRepository userRepository;
    @Mock private com.sseulang.domain.user.application.UserApplicationService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private com.sseulang.domain.auth.application.EmailVerificationService verificationService;

    private LocalAuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties("0123456789012345678901234567890123", 1800L, 604800L);
        service = new LocalAuthService(userRepository, userService, passwordEncoder, jwtProvider, refreshTokenStore, verificationService, props);
    }

    private User savedUser(String hashedPw) {
        User u = User.createLocalUser(EMAIL, hashedPw, "로컬");
        ReflectionTestUtils.setField(u, "id", 7L);
        return u;
    }

    @Test
    @DisplayName("signup 정상_BCrypt 해싱 + AT/RT 발급")
    void signup_정상() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 7L);
            return u;
        });
        when(jwtProvider.issueAccessToken(7L, "USER")).thenReturn("at-token");
        when(refreshTokenStore.currentTokenVersion("USER", 7L)).thenReturn(0L);
        when(jwtProvider.issueRefreshToken(7L, "USER", 0L)).thenReturn("rt-token");
        when(jwtProvider.parse("rt-token")).thenReturn(new JwtClaims(7L, "USER", "rt-jti", 0L, null, null));

        TokenPair pair = service.signup(EMAIL, "password123", "로컬");

        assertThat(pair.accessToken()).isEqualTo("at-token");
        assertThat(pair.refreshToken()).isEqualTo("rt-token");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("signup 짧은 password_AUTH_PASSWORD_INVALID")
    void signup_password_짧음() {
        assertThatThrownBy(() -> service.signup(EMAIL, "short", "로컬"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_PASSWORD_INVALID);
    }

    @Test
    @DisplayName("signup BCrypt 72-byte 초과 password_AUTH_PASSWORD_INVALID")
    void signup_password_길이초과() {
        String tooLong = "a".repeat(73);
        assertThatThrownBy(() -> service.signup(EMAIL, tooLong, "로컬"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_PASSWORD_INVALID);
    }

    @Test
    @DisplayName("signup 한글 25자 password (75 bytes UTF-8)_BCrypt truncation 회귀 차단 (게이트 1 round 2)")
    void signup_password_한글_75bytes_거부() {
        String multiByte = "가".repeat(25);  // 한글 1자 = 3 bytes UTF-8 → 75 bytes
        assertThatThrownBy(() -> service.signup(EMAIL, multiByte, "로컬"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_PASSWORD_INVALID);
    }

    @Test
    @DisplayName("login 한글 25자 password (75 bytes UTF-8)_AUTH_LOGIN_FAILED (게이트 1 round 2 login byte guard 회귀)")
    void login_password_한글_75bytes_거부() {
        String multiByte = "가".repeat(25);
        // user 시드 없어도 byte 가드가 먼저 트리거 — repo 조회 전 거부.
        assertThatThrownBy(() -> service.login(EMAIL, multiByte))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
    }

    @Test
    @DisplayName("signup 사전 email 중복_USER_EMAIL_DUPLICATED + save 호출 X")
    void signup_사전_중복() {
        User existing = savedUser("$2a$10$other");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.signup(EMAIL, "password123", "로컬"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_EMAIL_DUPLICATED);
        verify(userRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("signup UNIQUE race 패배_USER_EMAIL_DUPLICATED 변환")
    void signup_race_패배() {
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.empty())  // 사전 체크
                .thenReturn(Optional.of(savedUser("$2a$10$hashed")));  // race winner 발견
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> service.signup(EMAIL, "password123", "로컬"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.USER_EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("login 정상_BCrypt matches + AT/RT 발급")
    void login_정상() {
        User user = savedUser("$2a$10$realhash");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq("password123"), eq("$2a$10$realhash"))).thenReturn(true);
        when(jwtProvider.issueAccessToken(7L, "USER")).thenReturn("at-token");
        when(refreshTokenStore.currentTokenVersion("USER", 7L)).thenReturn(0L);
        when(jwtProvider.issueRefreshToken(7L, "USER", 0L)).thenReturn("rt-token");
        when(jwtProvider.parse("rt-token")).thenReturn(new JwtClaims(7L, "USER", "rt-jti", 0L, null, null));

        TokenPair pair = service.login(EMAIL, "password123");
        assertThat(pair.accessToken()).isEqualTo("at-token");
    }

    @Test
    @DisplayName("login 미존재 email_AUTH_LOGIN_FAILED + dummy hash 로 BCrypt 강제 (timing leak 방어)")
    void login_미존재_dummy_BCrypt() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(EMAIL, "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        // 핵심: 미존재 email 분기에서도 passwordEncoder.matches 가 호출됨 (timing 균일)
        verify(passwordEncoder).matches(eq("password123"), any());
    }

    @Test
    @DisplayName("login 소셜 가입자 (password 없음)_AUTH_LOGIN_FAILED + dummy hash matches 호출")
    void login_소셜가입자_local_거부() {
        User social = User.createSocialUser(SocialProvider.KAKAO, "k-1", EMAIL, "kakao", null);
        ReflectionTestUtils.setField(social, "id", 7L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(social));

        assertThatThrownBy(() -> service.login(EMAIL, "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        verify(passwordEncoder).matches(eq("password123"), any());  // dummy hash 로 timing 균일
    }

    @Test
    @DisplayName("login 차단된 user_AUTH_LOGIN_FAILED")
    void login_차단_거부() {
        User blocked = savedUser("$2a$10$realhash");
        ReflectionTestUtils.setField(blocked, "blocked", true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(blocked));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);  // 비밀번호 맞아도 거부

        assertThatThrownBy(() -> service.login(EMAIL, "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
    }

    @Test
    @DisplayName("dummyPasswordHash 가 BCrypt 표준 형식 (게이트 2 회귀: 형식 깨짐 차단)")
    void dummyHash_실제_BCrypt_패턴() {
        BCryptPasswordEncoder real = new BCryptPasswordEncoder();
        JwtProperties props = new JwtProperties("0123456789012345678901234567890123", 1800L, 604800L);
        LocalAuthService realSvc = new LocalAuthService(userRepository, userService, real, jwtProvider, refreshTokenStore, verificationService, props);

        String dummyHash = (String) ReflectionTestUtils.getField(realSvc, "dummyPasswordHash");
        assertThat(dummyHash)
                .as("BCrypt 표준 60자 패턴 — 깨지면 미존재 user 분기에서 즉시 false 반환해 timing leak 부활")
                .matches("^\\$2[ayb]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    }
}
