package com.sseulang.domain.item.infrastructure.persistence;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.QItem;
import com.sseulang.domain.item.domain.QItemHashtag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Item 동적 검색 — QueryDSL BooleanBuilder.
 *
 * <p>q 검색은 현재 단순 {@code LIKE '%q%'} 로, V1 스키마의 FULLTEXT(ngram) 인덱스를 활용하지 못한다.
 * 데이터 규모가 커지면 풀스캔 위험 — MATCH AGAINST 마이그는 별도 트래킹: <a href="https://github.com/sseullaeng/sl-server/issues/10">issue #10</a>.</p>
 */
@Repository
public class ItemQuerydslRepository {

    private final JPAQueryFactory queryFactory;

    public ItemQuerydslRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Page<Item> search(ItemSearchCriteria criteria, Pageable pageable) {
        QItem item = QItem.item;
        BooleanBuilder where = new BooleanBuilder();
        where.and(item.status.ne(ItemStatus.삭제));

        if (criteria.q() != null && !criteria.q().isBlank()) {
            String pattern = "%" + criteria.q().strip() + "%";
            where.and(item.title.like(pattern).or(item.description.like(pattern)));
        }
        if (criteria.categoryId() != null) {
            where.and(item.categoryId.eq(criteria.categoryId()));
        }
        if (criteria.tradeType() != null) {
            where.and(item.tradeType.eq(criteria.tradeType()));
        }
        if (criteria.minPrice() != null) {
            where.and(item.price.goe(criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            where.and(item.price.loe(criteria.maxPrice()));
        }
        if (criteria.tag() != null && !criteria.tag().isBlank()) {
            QItemHashtag h = QItemHashtag.itemHashtag;
            where.and(JPAExpressions
                    .selectOne()
                    .from(h)
                    .where(h.item.eq(item).and(h.tag.eq(criteria.tag().strip())))
                    .exists());
        }

        List<Item> content = queryFactory
                .selectFrom(item)
                .where(where)
                .orderBy(item.createdAt.desc(), item.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(item.count())
                .from(item)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }
}
