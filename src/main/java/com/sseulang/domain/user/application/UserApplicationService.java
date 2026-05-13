package com.sseulang.domain.user.application;

import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserApplicationService {

    private static final Logger log = LoggerFactory.getLogger(UserApplicationService.class);

    
    private static final int DORMANT_THRESHOLD_DAYS = 90;
    private static final String USER_ROLE = "USER";

    private final UserRepository userRepository;
    private final com.sseulang.domain.transaction.domain.TransactionRepository transactionRepository;
    private final com.sseulang.domain.report.domain.UserReportRepository userReportRepository;
    private final com.sseulang.domain.auth.domain.RefreshTokenStore refreshTokenStore;
    private final com.sseulang.domain.auth.domain.EmailSender emailSender;
    private final java.time.Clock clock;

    public UserApplicationService(
            UserRepository userRepository,
            com.sseulang.domain.transaction.domain.TransactionRepository transactionRepository,
            com.sseulang.domain.report.domain.UserReportRepository userReportRepository,
            com.sseulang.domain.auth.domain.RefreshTokenStore refreshTokenStore,
            com.sseulang.domain.auth.domain.EmailSender emailSender,
            java.time.Clock clock
    ) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.userReportRepository = userReportRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    

    @Transactional
    public User findOrCreateBySocial(
            SocialProvider provider,
            String providerId,
            Email email,
            String nickname,
            String profileImage
    ) {
        return userRepository.findBySocial(provider, providerId)
                .orElseGet(() -> linkOrCreate(provider, providerId, email, nickname, profileImage));
    }

    

    private User linkOrCreate(SocialProvider provider, String providerId, Email email, String nickname, String profileImage) {
        Optional<User> sameEmail = userRepository.findByEmail(email);
        if (sameEmail.isPresent()) {
            User existing = sameEmail.get();
            if (existing.getSocialProvider() == SocialProvider.LOCAL) {
                if (!existing.isEmailVerified()) {
                    // 미인증 LOCAL — squatting 방어 takeover (password 무효화).
                    existing.linkSocial(provider, providerId);
                    return existing;
                }
                // 인증된 LOCAL — 사용자 명시 연결 필요.
                throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_REQUIRED);
            }

            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
        }
        return create(provider, providerId, email, nickname, profileImage);
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User addSocialLink(Long userId, SocialProvider provider, String providerId, String providerEmail) {
        User user = getById(userId);
        if (user.getSocialProvider() != null && user.getSocialProvider() != SocialProvider.LOCAL) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_NOT_LOCAL);
        }
        if (providerEmail == null || !providerEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_EMAIL_MISMATCH);
        }
        userRepository.findBySocial(provider, providerId).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
            }
        });
        user.addSocialLink(provider, providerId);
        return user;
    }

    

    @Transactional
    public User updateProfile(Long userId, String profileImage, String nickname) {
        User user = getById(userId);
        user.updateProfile(profileImage, nickname);
        return user;
    }

    

    public void requireVerified(Long userId) {
        User user = getById(userId);
        if (!user.isAccessibleAt(java.time.LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.USER_BLOCKED);
        }
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
        }
    }

    

    @Transactional
    public void recordReview(Long revieweeId, int rating) {
        userRepository.recordReviewFor(revieweeId, rating);
    }

    

    @Transactional
    public void creditPoint(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.creditPointBalance(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    

    @Transactional
    public void deductPoint(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.deductPointBalance(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }
    }

    

    public long getPointBalance(Long userId) {
        Long balance = userRepository.findPointBalance(userId);
        if (balance == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return balance;
    }

    

    @Transactional
    public void holdForEscrow(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.holdForEscrow(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }
    }

    

    @Transactional
    public void releaseHold(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.releaseHold(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.TRANSACTION_HOLD_FAILED);
        }
    }

    

    @Transactional
    public void refundHold(Long userId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 는 양수여야 합니다");
        }
        int affected = userRepository.refundHold(userId, amount);
        if (affected != 1) {
            throw new BusinessException(ErrorCode.TRANSACTION_HOLD_FAILED);
        }
    }

    
    public long countSignupsBetween(java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return userRepository.countSignupsBetween(from, to);
    }

    
    public java.util.List<UserRepository.DailyCount> findDailySignups(
            java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return userRepository.findDailySignups(from, to);
    }

    
    public long getPointHold(Long userId) {
        Long hold = userRepository.findPointHold(userId);
        if (hold == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return hold;
    }

    

    public com.sseulang.domain.user.domain.UserRepository.PointSnapshot getPointSnapshot(Long userId) {
        return userRepository.findPointSnapshot(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    

    public Page<User> adminFindAll(Pageable pageable) {
        return userRepository.findAllForAdmin(pageable);
    }

    

    public Page<com.sseulang.domain.user.application.dto.AdminUserResult> adminSearch(
            com.sseulang.domain.user.application.dto.AdminUserSearchCriteria criteria,
            Pageable pageable
    ) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        Page<User> page = userRepository.searchForAdmin(criteria, now, DORMANT_THRESHOLD_DAYS, pageable);
        if (page.isEmpty()) {
            return page.map(u -> com.sseulang.domain.user.application.dto.AdminUserResult.from(
                    u, now, DORMANT_THRESHOLD_DAYS, 0L, 0L));
        }
        java.util.List<Long> ids = page.getContent().stream().map(User::getId).toList();
        java.util.Map<Long, Long> tradeCounts = transactionRepository.countByUserIdsAsParticipant(ids);
        java.util.Map<Long, Long> reportCounts = userReportRepository.countByTargetUserIds(ids);
        return page.map(u -> com.sseulang.domain.user.application.dto.AdminUserResult.from(
                u, now, DORMANT_THRESHOLD_DAYS,
                tradeCounts.getOrDefault(u.getId(), 0L),
                reportCounts.getOrDefault(u.getId(), 0L)
        ));
    }

    
    public com.sseulang.domain.user.application.dto.AdminUserResult adminGetEnriched(Long userId) {
        User u = getById(userId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        java.util.List<Long> ids = java.util.List.of(userId);
        long trades = transactionRepository.countByUserIdsAsParticipant(ids).getOrDefault(userId, 0L);
        long reports = userReportRepository.countByTargetUserIds(ids).getOrDefault(userId, 0L);
        return com.sseulang.domain.user.application.dto.AdminUserResult.from(u, now, DORMANT_THRESHOLD_DAYS, trades, reports);
    }

    

    @Transactional
    public void adminSetBlocked(Long userId, boolean blocked) {
        User u = getById(userId);
        if (blocked) {
            u.block();
            
            refreshTokenStore.revokeAll(USER_ROLE, userId);
        } else {
            u.unblock();
        }
    }

    

    @Transactional
    public void adminSuspend(Long userId, int days) {
        User u = getById(userId);
        u.suspend(days, java.time.LocalDateTime.now(clock));
        refreshTokenStore.revokeAll(USER_ROLE, userId);
        if (u.isAutoWithdrawTarget()) {
            u.markAutoWithdrawn();
            sendAutoWithdrawnNotice(u);
        }
    }

    
    private void sendAutoWithdrawnNotice(User u) {
        try {
            emailSender.sendAutoWithdrawnEmail(u.email().value(), u.getCumulativeSuspendDays());
        } catch (RuntimeException e) {
            log.error("[auto-withdraw] 안내 메일 발송 실패 userId={} reason={}", u.getId(), e.getMessage(), e);
        }
    }

    
    @Transactional
    public void adminUnsuspend(Long userId) {
        User u = getById(userId);
        u.unsuspend();
    }

    

    public java.util.List<Long> findAutoWithdrawTargetIds(int limit) {
        return userRepository.findAutoWithdrawTargetIds(200, limit);
    }

    

    @Transactional
    public boolean processAutoWithdrawal(Long userId) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null || !u.isAutoWithdrawTarget()) {
            return false;
        }
        u.markAutoWithdrawn();
        refreshTokenStore.revokeAll(USER_ROLE, userId);
        sendAutoWithdrawnNotice(u);
        return true;
    }

    

    public java.util.List<Long> findUserIdsByKeyword(String keyword, int limit) {
        return userRepository.findIdsByKeywordLike(keyword, limit);
    }

    // 라운드 12 — admin 화면용 nickname/profile 배치 조회.
    public java.util.Map<Long, UserProjection> findProjectionsByIds(java.util.Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return java.util.Map.of();
        java.util.Map<Long, UserProjection> map = new java.util.HashMap<>();
        for (Long id : userIds) {
            userRepository.findById(id).ifPresent(u ->
                    map.put(u.getId(), new UserProjection(u.getId(), u.getNickname(), u.getProfileImage())));
        }
        return map;
    }

    public record UserProjection(Long id, String nickname, String profileImage) { }

    
    @Transactional
    public void recordLogin(Long userId) {
        userRepository.findById(userId).ifPresent(u -> u.recordLogin(java.time.LocalDateTime.now(clock)));
    }

    

    public UserStatsResult adminGetStats() {
        long total = userRepository.countAll();
        long blocked = userRepository.countBlocked();
        long deleted = userRepository.countDeleted();
        long active = userRepository.countActive();
        return new UserStatsResult(total, blocked, deleted, active);
    }

    private User create(SocialProvider provider, String providerId, Email email, String nickname, String profileImage) {
        User newUser = User.createSocialUser(provider, providerId, email, nickname, profileImage);
        try {
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException race) {
            
            
            return resolveRaceOrRethrow(provider, providerId, email, race);
        }
    }

    private User resolveRaceOrRethrow(
            SocialProvider provider, String providerId, Email email, DataIntegrityViolationException race
    ) {
        
        Optional<User> raceWinner = userRepository.findBySocial(provider, providerId);
        if (raceWinner.isPresent()) {
            return raceWinner.get();
        }
        
        Optional<User> sameEmail = userRepository.findByEmail(email);
        if (sameEmail.isPresent()) {
            User existing = sameEmail.get();
            if (existing.getSocialProvider() == SocialProvider.LOCAL) {
                if (!existing.isEmailVerified()) {
                    existing.linkSocial(provider, providerId);
                    return existing;
                }
                throw new BusinessException(ErrorCode.AUTH_OAUTH_LINK_REQUIRED);
            }
            throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_LINKED_TO_DIFFERENT_PROVIDER);
        }

        throw race;
    }
}
