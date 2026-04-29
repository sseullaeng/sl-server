package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA — {@link ItemRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface ItemJpaRepository extends JpaRepository<Item, Long> {
}
