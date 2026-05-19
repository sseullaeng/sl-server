package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.event.EmailDispatchRequestedEvent;
import com.sseulang.domain.auth.domain.EmailSender;
import com.sseulang.domain.auth.domain.EmailVerification;
import com.sseulang.domain.auth.domain.EmailVerificationRepository;
import com.sseulang.domain.auth.domain.VerificationPurpose;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    

    private final Map<Long, LocalDateTime> lastIssuedAt = new ConcurrentHashMap<>();

    private final EmailVerificationRepository verificationRepository;
    private final UserApplicationService userService;
    private final EmailSender emailSender;  
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final String verificationUrlBase;

    public EmailVerificationService(
            EmailVerificationRepository verificationRepository,
            UserApplicationService userService,
            EmailSender emailSender,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.email.verification-url-base:http://localhost:8080/api/v1/auth/verify-email}")
            String verificationUrlBase
    ) {
        this.verificationRepository = verificationRepository;
        this.userService = userService;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.verificationUrlBase = verificationUrlBase;
    }

    

    

    @Transactional
    public void issueSignupToken(Long userId, String email) {
        LocalDateTime now = LocalDateTime.now(clock);
        verificationRepository.invalidateUnusedSignupTokens(userId, now);
        String token = generateToken();
        LocalDateTime expiresAt = now.plus(TOKEN_TTL);
        verificationRepository.save(EmailVerification.issue(userId, token, VerificationPurpose.SIGNUP, expiresAt));
        
        eventPublisher.publishEvent(new EmailDispatchRequestedEvent(email, verificationUrlBase + "?token=" + token));
        lastIssuedAt.put(userId, now);
    }

    

    @Transactional
    public void verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int affected = verificationRepository.markUsedIfValid(token, now);
        if (affected == 0) {
            
            EmailVerification ver = verificationRepository.findByToken(token)
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID));
            if (ver.isUsed()) {
                throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
            }
            if (ver.isExpired(now)) {
                throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_EXPIRED);
            }
            
            throw new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID);
        }
        
        EmailVerification ver = verificationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID));
        User user = userService.getById(ver.getUserId());
        user.markEmailVerified();
    }

    

    @Transactional
    public void resendForUser(Long userId) {
        User user = userService.getById(userId);
        if (user.isEmailVerified()) {
            return;  
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime last = lastIssuedAt.get(userId);
        if (last != null && Duration.between(last, now).compareTo(RESEND_COOLDOWN) < 0) {
            throw new com.sseulang.global.exception.BusinessException(
                    com.sseulang.global.exception.ErrorCode.AUTH_VERIFICATION_RESEND_TOO_SOON);
        }
        issueSignupToken(user.getId(), user.getEmail());
    }

    private static String generateToken() {
        
        return UUID.randomUUID().toString().replace("-", "");
    }
}
