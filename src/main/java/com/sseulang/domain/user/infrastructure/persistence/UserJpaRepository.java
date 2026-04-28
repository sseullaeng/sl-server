package com.sseulang.domain.user.infrastructure.persistence;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA — {@link UserRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findBySocialProviderAndSocialId(SocialProvider provider, String socialId);

    Optional<User> findByEmail(String email);
}
