package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.Map;

/**
 * Chat 도메인이 채팅방 응답에 아이템 제목/썸네일을 자동 join 으로 채울 때 쓰는 read-only 포트.
 * 구현은 {@code item.infrastructure} 어댑터.
 *
 * <p>CLAUDE.md §3.3 — 단방향 의존, N+1 회피용 batch read.</p>
 */
public interface ItemView {

    /**
     * itemId 들에 대한 (id → projection) 맵. 미존재 id 는 맵에서 빠짐 (삭제된 아이템 등).
     * 비어있으면 빈 맵 반환 — DB 호출 안 함.
     */
    Map<Long, ItemProjection> findByIds(Collection<Long> itemIds);

    /**
     * 채팅 응답용 아이템 최소 projection. 제목 + 썸네일 + 판매자 id (viewer 본인=isSeller 판정용).
     * 라운드 12 PR-C #6 — 시스템 카드 생성 시 price 도 필요해 필드 추가 (snapshot).
     */
    record ItemProjection(Long id, String title, String thumbnailUrl, Long sellerId, Long price) { }
}
