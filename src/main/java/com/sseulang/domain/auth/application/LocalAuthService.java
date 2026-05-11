package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.dto.TokenPair;
import com.sseulang.domain.auth.domain.RefreshTokenStore;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.security.JwtProperties;
import com.sseulang.global.security.JwtProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class LocalAuthService {

    private static final String DEFAULT_ROLE = "USER";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;

    

    private static final String DUMMY_PASSWORD_PLAINTEXT = "__sseulang_dummy_user_sentinel_v1__";

    private final UserRepository userRepository;
    private final com.sseulang.domain.user.application.UserApplicationService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final EmailVerificationService verificationService;
    private final Duration refreshTokenTtl;
    

    private final String dummyPasswordHash;

    public LocalAuthService(
            UserRepository userRepository,
            com.sseulang.domain.user.application.UserApplicationService userService,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            EmailVerificationService verificationService,
            JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.verificationService = verificationService;
        this.refreshTokenTtl = Duration.ofSeconds(jwtProperties.refreshTokenValiditySeconds());
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD_PLAINTEXT);
    }

    

    @Transactional
    public TokenPair signup(Email email, String rawPassword, String nickname) {
        validatePassword(rawPassword);

        
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
        });

        String hashed = passwordEncoder.encode(rawPassword);
        User saved;
        try {
            saved = userRepository.save(User.createLocalUser(email, hashed, nickname));
        } catch (DataIntegrityViolationException race) {
            
            if (userRepository.findByEmail(email).isPresent()) {
                throw new BusinessException(ErrorCode.USER_EMAIL_DUPLICATED);
            }
            throw race;
        }
        
        
        verificationService.issueSignupToken(saved.getId(), saved.getEmail());
        return issueTokens(saved);
    }

    

    public TokenPair login(Email email, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        
        
        if (rawPassword.length() > MAX_PASSWORD_LENGTH
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        
        String hashToCompare = (user != null && user.hasPassword()) ? user.getPassword() : dummyPasswordHash;
        boolean passwordOk = passwordEncoder.matches(rawPassword, hashToCompare);

        
        
        boolean accessible = user != null && user.isAccessibleAt(java.time.LocalDateTime.now());
        if (user == null || !user.hasPassword() || !accessible || !passwordOk) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        
        userService.recordLogin(user.getId());
        return issueTokens(user);
    }

    

    private void validatePassword(String rawPassword) {
        if (rawPassword == null
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_INVALID);
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_INVALID);
        }
    }

    private TokenPair issueTokens(User user) {
        String at = jwtProvider.issueAccessToken(user.getId(), DEFAULT_ROLE);
        long tv = refreshTokenStore.currentTokenVersion(DEFAULT_ROLE, user.getId());
        String rt = jwtProvider.issueRefreshToken(user.getId(), DEFAULT_ROLE, tv);
        String rtJti = jwtProvider.parse(rt).jti();
        refreshTokenStore.save(DEFAULT_ROLE, user.getId(), rtJti, refreshTokenTtl);
        return new TokenPair(at, rt);
    }
}
