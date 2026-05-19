package com.sseulang.domain.user.infrastructure.persistence;

import com.sseulang.domain.chat.domain.UserView;
import com.sseulang.domain.user.domain.User;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserViewAdapter implements UserView {

    private final UserJpaRepository jpa;

    public UserViewAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<Long, UserProjection> findByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProjection> result = new HashMap<>();
        for (User u : jpa.findAllById(userIds)) {
            result.put(u.getId(), new UserProjection(u.getId(), u.getNickname(), u.getProfileImage()));
        }
        return result;
    }
}
