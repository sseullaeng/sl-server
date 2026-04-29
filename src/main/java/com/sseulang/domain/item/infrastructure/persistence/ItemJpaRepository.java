package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import jakarta.persistence.LockModeType;
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
}
