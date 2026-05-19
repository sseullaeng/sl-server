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

@Service
public class AdminLoginService {

    private static final String ADMIN_ROLE = "ADMIN";

    

    private static final String DUMMY_PASSWORD_PLAINTEXT = "__sseulang_dummy_admin_sentinel_v1__";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final Duration refreshTokenTtl;
    

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
