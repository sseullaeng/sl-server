package com.sseulang.domain.item.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Item Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 검색·필터(QueryDSL) 는 별도 작업에서 추가.
 */
public interface ItemRepository {

    Optional<Item> findById(Long id);

    /** 삭제 상태가 아닌 물품을 최신순으로 페이지 단위 조회. */
    Page<Item> findVisibleLatest(Pageable pageable);

    Item save(Item item);

    void delete(Item item);
}
