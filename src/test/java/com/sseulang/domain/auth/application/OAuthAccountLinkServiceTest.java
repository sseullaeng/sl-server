package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.OAuthLinkKeyStore;
import com.sseulang.domain.auth.domain.OAuthProvider;
import com.sseulang.domain.auth.domain.OAuthUserInfo;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthAccountLinkServiceTest {

    private static final Long USER_ID = 7L;
    private static final Email EMAIL = new Email("owner@example.com");
    private static final OAuthUserInfo INFO_MATCH =
            new OAuthUserInfo(SocialProvider.KAKAO, "k-1", EMAIL, "n", null);

    private OAuthProvider kakaoProvider;
    private UserApplicationService userService;
    private OAuthLinkKeyStore store;
    private OAuthAccountLinkService service;

    @BeforeEach
    void setUp() {
        kakaoProvider = mock(OAuthProvider.class);
        when(kakaoProvider.supports()).thenReturn(SocialProvider.KAKAO);
        userService = mock(UserApplicationService.class);
        store = new InMemoryLinkKeyStore();
        service = new OAuthAccountLinkService(List.of(kakaoProvider), userService, store);
    }

    private User mockLocalUser() {
        User u = mock(User.class);
        when(u.getId()).thenReturn(USER_ID);
        when(u.getEmail()).thenReturn(EMAIL.value());
        when(u.getSocialProvider()).thenReturn(SocialProvider.LOCAL);
        return u;
    }

    @Test
    @DisplayName("preview_정상_linkKey 발급 + store 저장")
    void preview_정상() {
        User u = mockLocalUser();
        when(userService.getById(USER_ID)).thenReturn(u);
        when(kakaoProvider.exchangeCodeAndFetch("CODE", "https://cb")).thenReturn(INFO_MATCH);

        var r = service.preview(USER_ID, SocialProvider.KAKAO, "CODE", "https://cb");

        assertThat(r.linkKey()).isNotBlank();
        assertThat(r.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(r.providerEmail()).isEqualTo(EMAIL.value());
        assertThat(r.expiresInSeconds()).isEqualTo(300);

        var v = store.consume(r.linkKey()).orElseThrow();
        assertThat(v.userId()).isEqualTo(USER_ID);
        assertThat(v.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(v.providerId()).isEqualTo("k-1");
    }

    @Test
    @DisplayName("preview_email 불일치_AUTH_OAUTH_LINK_EMAIL_MISMATCH + store 저장 안 함")
    void preview_이메일불일치() {
        User u = mockLocalUser();
        when(userService.getById(USER_ID)).thenReturn(u);
        OAuthUserInfo other = new OAuthUserInfo(SocialProvider.KAKAO, "k-1",
                new Email("other@example.com"), "n", null);
        when(kakaoProvider.exchangeCodeAndFetch("CODE", "https://cb")).thenReturn(other);

        assertThatThrownBy(() -> service.preview(USER_ID, SocialProvider.KAKAO, "CODE", "https://cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_EMAIL_MISMATCH);
    }

    @Test
    @DisplayName("preview_이미 소셜 연결된 사용자_AUTH_OAUTH_LINK_NOT_LOCAL")
    void preview_이미연결됨() {
        User u = mock(User.class);
        when(u.getSocialProvider()).thenReturn(SocialProvider.GOOGLE);
        when(userService.getById(USER_ID)).thenReturn(u);

        assertThatThrownBy(() -> service.preview(USER_ID, SocialProvider.KAKAO, "CODE", "https://cb"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_NOT_LOCAL);
        verify(kakaoProvider, never()).exchangeCodeAndFetch(any(), any());
    }

    @Test
    @DisplayName("confirm_정상_userService.addSocialLink 호출 + 키 1회 사용")
    void confirm_정상() {
        store.save("KEY", USER_ID, SocialProvider.KAKAO, "k-1", Duration.ofMinutes(5));
        User u = mockLocalUser();
        when(userService.getById(USER_ID)).thenReturn(u);
        User updated = mock(User.class);
        when(userService.addSocialLink(eq(USER_ID), eq(SocialProvider.KAKAO), eq("k-1"), eq(EMAIL.value())))
                .thenReturn(updated);

        User result = service.confirm(USER_ID, "KEY");

        assertThat(result).isSameAs(updated);
        // 키 1회용 — 재사용 불가
        assertThatThrownBy(() -> service.confirm(USER_ID, "KEY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_KEY_INVALID);
    }

    @Test
    @DisplayName("confirm_다른 user 의 linkKey_AUTH_OAUTH_LINK_KEY_INVALID")
    void confirm_타사용자키() {
        store.save("KEY", 999L, SocialProvider.KAKAO, "k-1", Duration.ofMinutes(5));

        assertThatThrownBy(() -> service.confirm(USER_ID, "KEY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_OAUTH_LINK_KEY_INVALID);
    }

    private static final class InMemoryLinkKeyStore implements OAuthLinkKeyStore {
        private final Map<String, LinkKeyValue> store = new HashMap<>();

        @Override
        public void save(String key, Long userId, SocialProvider provider, String providerId, Duration ttl) {
            store.put(key, new LinkKeyValue(userId, provider, providerId));
        }

        @Override
        public Optional<LinkKeyValue> consume(String key) {
            return Optional.ofNullable(store.remove(key));
        }
    }
}
