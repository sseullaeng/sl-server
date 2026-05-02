package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.UserView;

import java.util.Collection;
import java.util.Map;

/** 단위 테스트용 fake — 항상 빈 맵 반환. opponentNickname/profileImage 는 null. */
public class NoOpUserView implements UserView {
    @Override
    public Map<Long, UserProjection> findByIds(Collection<Long> userIds) {
        return Map.of();
    }
}
