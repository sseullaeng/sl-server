package com.sseulang.domain.wishlist.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.wishlist.domain.Wishlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WishlistJpaRepository extends JpaRepository<Wishlist, Long> {

    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    @Modifying
    @Query("DELETE FROM Wishlist w WHERE w.userId = :userId AND w.itemId = :itemId")
    int deleteByUserAndItem(@Param("userId") Long userId, @Param("itemId") Long itemId);

    

    @Query("SELECT w.itemId FROM Wishlist w WHERE w.userId = :userId AND w.itemId IN :itemIds")
    java.util.List<Long> findItemIdsByUserIdAndItemIds(
            @Param("userId") Long userId,
            @Param("itemIds") java.util.Collection<Long> itemIds);

    

    @Query(value = """
            SELECT i FROM Item i
             WHERE i.id IN (SELECT w.itemId FROM Wishlist w WHERE w.userId = :userId)
               AND i.status != com.sseulang.domain.item.domain.ItemStatus.삭제
             ORDER BY (SELECT w.createdAt FROM Wishlist w WHERE w.userId = :userId AND w.itemId = i.id) DESC
            """,
            countQuery = """
            SELECT COUNT(i) FROM Item i
             WHERE i.id IN (SELECT w.itemId FROM Wishlist w WHERE w.userId = :userId)
               AND i.status != com.sseulang.domain.item.domain.ItemStatus.삭제
            """)
    Page<Item> findWishlistedItems(@Param("userId") Long userId, Pageable pageable);
}
