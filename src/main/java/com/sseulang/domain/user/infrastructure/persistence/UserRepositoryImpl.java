package com.sseulang.domain.user.infrastructure.persistence;

import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.user.domain.UserRepository;
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
}
