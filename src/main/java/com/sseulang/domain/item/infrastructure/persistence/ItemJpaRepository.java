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

    
    @Query("SELECT i.wishlistCount FROM Item i WHERE i.id = :id")
    Optional<Integer> getWishlistCount(@Param("id") Long id);

    

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
