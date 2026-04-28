package com.sseulang.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        String domain,
        boolean secure,
        String sameSite
) {
    private static final Set<String> ALLOWED_SAME_SITE = Set.of("Strict", "Lax", "None");

    public CookieProperties {
        if (sameSite == null || sameSite.isBlank()) {
            sameSite = "Lax";
        }
        if (!ALLOWED_SAME_SITE.contains(sameSite)) {
            throw new IllegalArgumentException("app.cookie.same-site 는 Strict / Lax / None 중 하나여야 합니다");
        }
        // SameSite=None 인데 secure=false 이면 모던 브라우저가 무시함 → fail-fast
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None 은 secure=true 와 함께만 사용 가능합니다");
        }
    }
}
