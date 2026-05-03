package com.sseulang.domain.user.infrastructure.persistence;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryImpl(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<User> findBySocial(SocialProvider provider, String socialId) {
        return jpa.findBySocialProviderAndSocialId(provider, socialId);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpa.findByEmail(email.value());
    }

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public Page<User> findAllForAdmin(Pageable pageable) {
        return jpa.findAllByOrderByIdDesc(pageable);
    }

    @Override
    public Page<User> searchForAdmin(
            com.sseulang.domain.user.application.dto.AdminUserSearchCriteria criteria,
            java.time.LocalDateTime now,
            int dormantThresholdDays,
            Pageable pageable
    ) {
        String kw = (criteria.keyword() == null || criteria.keyword().isBlank())
                ? null : criteria.keyword().strip();
        String status = criteria.status() == null ? null : criteria.status().name();
        java.time.LocalDateTime dormantThreshold = now.minusDays(dormantThresholdDays);
        return jpa.searchAdmin(
                kw,
                criteria.createdAfter(),
                criteria.createdBefore(),
                status,
                dormantThreshold,
                now,
                pageable
        );
    }

    @Override
    public int recordReviewFor(Long revieweeId, int rating) {
        return jpa.recordReviewFor(revieweeId, rating);
    }

    @Override
    public int creditPointBalance(Long userId, long amount) {
        return jpa.creditPointBalance(userId, amount);
    }

    @Override
    public int deductPointBalance(Long userId, long amount) {
        return jpa.deductPointBalance(userId, amount);
    }

    @Override
    public Long findPointBalance(Long userId) {
        return jpa.findPointBalanceById(userId);
    }

    @Override
    public long countAll() {
        return jpa.count();
    }

    @Override
    public long countBlocked() {
        return jpa.countBlocked();
    }

    @Override
    public long countDeleted() {
        return jpa.countDeleted();
    }

    @Override
    public long countActive() {
        return jpa.countActive();
    }

    @Override
    public java.util.List<Long> findActiveIdsAfter(long afterId, int limit) {
        if (limit <= 0) return java.util.Collections.emptyList();
        return jpa.findActiveIdsAfter(afterId, org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
