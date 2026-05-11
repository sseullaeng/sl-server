package com.sseulang.domain.user.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findBySocial(SocialProvider provider, String socialId);

    Optional<User> findByEmail(Email email);

    User save(User user);

    
    Page<User> findAllForAdmin(Pageable pageable);

    

    Page<User> searchForAdmin(
            com.sseulang.domain.user.application.dto.AdminUserSearchCriteria criteria,
            java.time.LocalDateTime now,
            int dormantThresholdDays,
            Pageable pageable
    );

    

    int recordReviewFor(Long revieweeId, int rating);

    

    int creditPointBalance(Long userId, long amount);

    

    int deductPointBalance(Long userId, long amount);

    

    Long findPointBalance(Long userId);

    

    int holdForEscrow(Long userId, long amount);

    

    int releaseHold(Long userId, long amount);

    

    int refundHold(Long userId, long amount);

    

    Long findPointHold(Long userId);

    

    java.util.Optional<PointSnapshot> findPointSnapshot(Long userId);

    record PointSnapshot(long balance, long hold) { }

    

    
    long countAll();

    
    long countBlocked();

    
    long countDeleted();

    

    long countActive();

    

    long countSignupsBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    

    java.util.List<DailyCount> findDailySignups(java.time.LocalDateTime from, java.time.LocalDateTime to);

    
    record DailyCount(java.time.LocalDate date, long count) { }

    

    java.util.List<Long> findActiveIdsAfter(long afterId, int limit);

    

    java.util.List<Long> findIdsByKeywordLike(String keyword, int limit);

    

    java.util.List<Long> findAutoWithdrawTargetIds(int threshold, int limit);
}
