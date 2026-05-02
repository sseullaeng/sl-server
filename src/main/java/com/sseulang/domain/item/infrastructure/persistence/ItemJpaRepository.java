package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Spring Data JPA — {@link ItemRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface ItemJpaRepository extends JpaRepository<Item, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Item i SET i.wishlistCount = i.wishlistCount + 1 WHERE i.id = :id")
    int incrementWishlistCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Item i SET i.wishlistCount = i.wishlistCount - 1 WHERE i.id = :id AND i.wishlistCount > 0")
    int decrementWishlistCount(@Param("id") Long id);

    /** Fresh wishlist_count read — JPQL 스칼라 projection 으로 persistence context 우회. */
    @Query("SELECT i.wishlistCount FROM Item i WHERE i.id = :id")
    Optional<Integer> getWishlistCount(@Param("id") Long id);

    /**
     * 본인 물품 — status 명시 시 정확 일치, null 이면 삭제 제외 전체.
     * status 가 null 이면 cross-product 가 안 되므로 두 쿼리로 분기.
     */
    @Query("""
            SELECT i FROM Item i
             WHERE i.sellerId = :sellerId AND i.status = :status
             ORDER BY i.createdAt DESC, i.id DESC
            """)
    Page<Item> findBySellerIdAndStatus(
            @Param("sellerId") Long sellerId,
            @Param("status") ItemStatus status,
            Pageable pageable);

    @Query("""
            SELECT i FROM Item i
             WHERE i.sellerId = :sellerId AND i.status <> com.sseulang.domain.item.domain.ItemStatus.삭제
             ORDER BY i.createdAt DESC, i.id DESC
            """)
    Page<Item> findBySellerIdExcludingDeleted(
            @Param("sellerId") Long sellerId,
            Pageable pageable);
}
