package com.sseulang.domain.auth.domain;

import com.sseulang.domain.user.domain.SocialProvider;

public interface OAuthProvider {

    SocialProvider supports();

    

    OAuthUserInfo exchangeCodeAndFetch(String code, String redirectUri);
}
