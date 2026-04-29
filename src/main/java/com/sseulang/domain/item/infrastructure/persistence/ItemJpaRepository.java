package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA — {@link ItemRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface ItemJpaRepository extends JpaRepository<Item, Long> {

    @Query("SELECT i FROM Item i WHERE i.status <> com.sseulang.domain.item.domain.ItemStatus.삭제")
    Page<Item> findVisibleLatest(Pageable pageable);
}
