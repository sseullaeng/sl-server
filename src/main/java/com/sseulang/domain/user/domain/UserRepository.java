package com.sseulang.domain.user.domain;

import java.util.Optional;

/**
 * User Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/user/infrastructure/persistence}.
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findBySocial(SocialProvider provider, String socialId);

    Optional<User> findByEmail(Email email);

    User save(User user);
}
