package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.Map;

/**
 * Chat 도메인이 채팅방 응답에 상대방 닉네임/프로필을 자동 join 으로 채울 때 쓰는 read-only 포트.
 * 구현은 {@code user.infrastructure} 어댑터.
 *
 * <p>CLAUDE.md §3.3 — 다른 도메인 Repository 직접 호출 금지. 단방향 의존 + N+1 회피용 batch read.</p>
 */
public interface UserView {

    /**
     * userId 들에 대한 (id → projection) 맵. 미존재 id 는 맵에서 빠짐.
     * 비어있으면 빈 맵 반환 — DB 호출 안 함.
     */
    Map<Long, UserProjection> findByIds(Collection<Long> userIds);

    /** 채팅 응답용 사용자 최소 projection. 닉네임 + 프로필이미지만. */
    record UserProjection(Long id, String nickname, String profileImage) { }
}
