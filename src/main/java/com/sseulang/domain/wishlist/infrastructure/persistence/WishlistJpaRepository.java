package com.sseulang.domain.wishlist.infrastructure.persistence;

import com.sseulang.domain.wishlist.domain.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WishlistJpaRepository extends JpaRepository<Wishlist, Long> {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.userId = :userId AND w.itemId = :itemId")
    int deleteByUserAndItem(@Param("userId") Long userId, @Param("itemId") Long itemId);
}
