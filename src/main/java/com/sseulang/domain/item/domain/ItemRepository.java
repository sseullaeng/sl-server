package com.sseulang.domain.item.domain;

import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Item Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 검색·필터(QueryDSL) 구현은 {@code infrastructure/persistence/ItemQuerydslRepository}.
 */
public interface ItemRepository {

    Optional<Item> findById(Long id);

    /** 동적 검색·필터 + 최신순 정렬. criteria 의 모든 필드가 null 이면 전체 (status != 삭제) 최신순. */
    Page<Item> search(ItemSearchCriteria criteria, Pageable pageable);

    Item save(Item item);

    void delete(Item item);
}
