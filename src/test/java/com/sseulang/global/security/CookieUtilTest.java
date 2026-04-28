package com.sseulang.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookieUtilTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-for-hs256-blah-blah";
    private static final long AT_VALIDITY = 1800L;
    private static final long RT_VALIDITY = 604800L;

    private final JwtProperties jwt = new JwtProperties(SECRET, AT_VALIDITY, RT_VALIDITY);

    private CookieUtil prodLikeUtil() {
        return new CookieUtil(new CookieProperties("sseulang.kr", true, "Strict"), jwt);
    }

    private CookieUtil localLikeUtil() {
        return new CookieUtil(new CookieProperties("localhost", false, "Lax"), jwt);
    }

    @Test
    @DisplayName("accessTokenCookie_HttpOnly + Path / + MaxAge=AT 유효기간")
    void accessTokenCookie_기본속성() {
        ResponseCookie c = prodLikeUtil().accessTokenCookie("AT_VALUE");

        assertThat(c.getName()).isEqualTo("at");
        assertThat(c.getValue()).isEqualTo("AT_VALUE");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getPath()).isEqualTo("/");
        assertThat(c.getMaxAge()).isEqualTo(Duration.ofSeconds(AT_VALIDITY));
    }

    @Test
    @DisplayName("refreshTokenCookie_HttpOnly + Path /api/v1/auth + MaxAge=RT 유효기간")
    void refreshTokenCookie_기본속성() {
        ResponseCookie c = prodLikeUtil().refreshTokenCookie("RT_VALUE");

        assertThat(c.getName()).isEqualTo("rt");
        assertThat(c.getValue()).isEqualTo("RT_VALUE");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getPath()).isEqualTo("/api/v1/auth");
        assertThat(c.getMaxAge()).isEqualTo(Duration.ofSeconds(RT_VALIDITY));
    }

    @Test
    @DisplayName("delete 쿠키_value 비고 MaxAge=0")
    void deleteCookies_maxAge_0() {
        CookieUtil u = prodLikeUtil();

        assertThat(u.deleteAccessTokenCookie().getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(u.deleteAccessTokenCookie().getValue()).isEmpty();
        assertThat(u.deleteRefreshTokenCookie().getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(u.deleteRefreshTokenCookie().getValue()).isEmpty();
    }

    @Nested
    @DisplayName("환경별 속성")
    class EnvironmentSpecific {

        @Test
        @DisplayName("prod-like_secure=true + SameSite=Strict + domain=sseulang.kr")
        void prodLike() {
            ResponseCookie c = prodLikeUtil().accessTokenCookie("AT");

            assertThat(c.isSecure()).isTrue();
            assertThat(c.getSameSite()).isEqualTo("Strict");
            assertThat(c.getDomain()).isEqualTo("sseulang.kr");
        }

        @Test
        @DisplayName("local-like_secure=false + SameSite=Lax + domain=localhost")
        void localLike() {
            ResponseCookie c = localLikeUtil().accessTokenCookie("AT");

            assertThat(c.isSecure()).isFalse();
            assertThat(c.getSameSite()).isEqualTo("Lax");
            assertThat(c.getDomain()).isEqualTo("localhost");
        }

        @Test
        @DisplayName("domain 빈 문자열_쿠키 domain 미설정")
        void emptyDomain_omits() {
            CookieUtil u = new CookieUtil(new CookieProperties("", false, "Lax"), jwt);

            ResponseCookie c = u.accessTokenCookie("AT");

            assertThat(c.getDomain()).isNull();
        }
    }

    @Nested
    @DisplayName("CookieProperties 검증")
    class PropertiesValidation {

        @Test
        @DisplayName("허용 안 된 sameSite 값_예외")
        void invalidSameSite_예외() {
            assertThatThrownBy(() -> new CookieProperties("localhost", false, "Weird"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SameSite=None_secure=false_예외")
        void sameSiteNone_secureFalse_예외() {
            assertThatThrownBy(() -> new CookieProperties("localhost", false, "None"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SameSite=None_secure=true_OK")
        void sameSiteNone_secureTrue_ok() {
            CookieProperties p = new CookieProperties("a.com", true, "None");

            assertThat(p.sameSite()).isEqualTo("None");
            assertThat(p.secure()).isTrue();
        }

        @Test
        @DisplayName("sameSite null 또는 blank_기본값 Lax")
        void blankSameSite_defaultsToLax() {
            assertThat(new CookieProperties("a", false, null).sameSite()).isEqualTo("Lax");
            assertThat(new CookieProperties("a", false, "").sameSite()).isEqualTo("Lax");
        }
    }
}
