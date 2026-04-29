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

    /**
     * 비관적 쓰기 락(PESSIMISTIC_WRITE)으로 Item 조회. 가이드 §5.2 동시 거래 차단을 위한 핵심 —
     * Transaction 도메인이 reserve/complete/cancel 시 Item 행을 잠그고 상태 전이.
     * 첫 호출이 락 점유 → 후속 호출은 대기 → 락 해제 후 status 보고 적절히 거부 (TRANSACTION_RESERVED_BY_OTHER 등).
     */
    Optional<Item> findByIdForUpdate(Long id);

    /** 동적 검색·필터 + 최신순 정렬. criteria 의 모든 필드가 null 이면 전체 (status != 삭제) 최신순. */
    Page<Item> search(ItemSearchCriteria criteria, Pageable pageable);

    Item save(Item item);

    void delete(Item item);

    /**
     * {@code wishlist_count} 원자 증가. 영향받은 행 수 반환. 레이스 가능성 있는 카운터를 동시성 안전하게.
     */
    int incrementWishlistCount(Long itemId);

    /**
     * {@code wishlist_count} 원자 감소. 음수가 되지 않도록 {@code AND wishlist_count > 0} 조건 강제.
     * 영향받은 행 수 반환.
     */
    int decrementWishlistCount(Long itemId);
}
