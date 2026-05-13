package com.sseulang.domain.item.infrastructure.persistence;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.application.dto.ItemSort;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.QItem;
import com.sseulang.domain.item.domain.QItemHashtag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ItemQuerydslRepository {

    
    private static final int MIN_NGRAM_TOKEN_LENGTH = 2;

    private final JPAQueryFactory queryFactory;

    public ItemQuerydslRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Page<Item> search(ItemSearchCriteria criteria, Pageable pageable) {
        QItem item = QItem.item;
        BooleanBuilder where = new BooleanBuilder();
        where.and(item.status.ne(ItemStatus.삭제));

        if (criteria.q() != null && !criteria.q().isBlank()) {
            String input = criteria.q().strip();
            String booleanQuery = toBooleanModeQuery(input);
            if (!booleanQuery.isEmpty()) {
                NumberExpression<Double> matchScore = Expressions.numberTemplate(
                        Double.class,
                        "function('match_against', {0}, {1}, {2})",
                        item.title, item.description, booleanQuery
                );
                where.and(matchScore.gt(0));
            } else {
                
                String pattern = "%" + input + "%";
                where.and(item.title.like(pattern).or(item.description.like(pattern)));
            }
        }
        if (criteria.categoryId() != null) {
            where.and(item.categoryId.eq(criteria.categoryId()));
        }
        if (criteria.tradeType() != null) {
            // V25 다중모드 — trade_types CSV 컬럼에 FIND_IN_SET. 단일 trade_type 컬럼은 deprecated.
            where.and(Expressions.booleanTemplate(
                    "function('find_in_set', {0}, {1}) > 0",
                    criteria.tradeType().name(),
                    item.tradeTypes
            ));
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
        if (criteria.sellerId() != null) {
            where.and(item.sellerId.eq(criteria.sellerId()));
        }

        List<Item> content = queryFactory
                .selectFrom(item)
                .where(where)
                .orderBy(orderBy(criteria.sorts(), item))
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

    

    private static OrderSpecifier<?>[] orderBy(List<ItemSort> sorts, QItem item) {
        java.util.LinkedHashMap<String, OrderSpecifier<?>> dedup = new java.util.LinkedHashMap<>();
        for (ItemSort s : sorts) {
            for (OrderSpecifier<?> o : keyOrderSpecs(s, item)) {
                dedup.putIfAbsent(o.toString(), o);
            }
        }
        // 마지막 tiebreak — id desc 가 없으면 자동 부착(unique 보장).
        OrderSpecifier<?> idDesc = item.id.desc();
        dedup.putIfAbsent(idDesc.toString(), idDesc);
        return dedup.values().toArray(new OrderSpecifier<?>[0]);
    }

    private static OrderSpecifier<?>[] keyOrderSpecs(ItemSort sort, QItem item) {
        return switch (sort) {
            case PRICE_ASC -> new OrderSpecifier<?>[]{ item.price.asc() };
            case PRICE_DESC -> new OrderSpecifier<?>[]{ item.price.desc() };
            case VIEW_DESC -> new OrderSpecifier<?>[]{ item.viewCount.desc(), item.createdAt.desc() };
            case WISHLIST_DESC -> new OrderSpecifier<?>[]{ item.wishlistCount.desc(), item.createdAt.desc() };
            case COMPLETED_LAST -> new OrderSpecifier<?>[]{
                    new OrderSpecifier<>(com.querydsl.core.types.Order.ASC,
                            new com.querydsl.core.types.dsl.CaseBuilder()
                                    .when(item.status.eq(ItemStatus.거래완료)).then(1)
                                    .otherwise(0))
            };
            case LATEST -> new OrderSpecifier<?>[]{ item.createdAt.desc() };
        };
    }

    

    public java.util.Map<Long, java.util.List<String>> findHashtagsByItemIds(java.util.Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return java.util.Collections.emptyMap();
        QItemHashtag h = QItemHashtag.itemHashtag;
        QItem item = QItem.item;
        java.util.List<com.querydsl.core.Tuple> rows = queryFactory
                .select(h.item.id, h.tag)
                .from(h)
                .where(h.item.id.in(itemIds))
                .orderBy(h.id.asc())
                .fetch();
        java.util.Map<Long, java.util.List<String>> result = new java.util.LinkedHashMap<>();
        for (var row : rows) {
            result.computeIfAbsent(row.get(h.item.id), k -> new java.util.ArrayList<>())
                    .add(row.get(h.tag));
        }
        return result;
    }

    static String toBooleanModeQuery(String input) {
        if (input == null) return "";
        String sanitized = input.replaceAll("[+\\-*\"<>()~@]", " ").trim();
        if (sanitized.isEmpty()) return "";
        return Arrays.stream(sanitized.split("\\s+"))
                .filter(s -> s.length() >= MIN_NGRAM_TOKEN_LENGTH)
                .map(s -> "+" + s)
                .collect(Collectors.joining(" "));
    }

    // 라운드 12 — admin item 검색. 삭제 포함 모든 상태, REPORT_DESC 정렬은 service 후처리.
    public Page<Item> adminSearch(
            com.sseulang.domain.item.application.dto.AdminItemSearchCriteria criteria,
            java.util.Collection<Long> matchedSellerIds,
            Pageable pageable
    ) {
        QItem item = QItem.item;
        BooleanBuilder where = new BooleanBuilder();
        if (criteria.q() != null && !criteria.q().isBlank()) {
            String pattern = "%" + criteria.q().strip() + "%";
            BooleanBuilder kw = new BooleanBuilder(item.title.like(pattern));
            if (matchedSellerIds != null && !matchedSellerIds.isEmpty()) {
                kw.or(item.sellerId.in(matchedSellerIds));
            }
            where.and(kw);
        }
        if (criteria.status() != null) where.and(item.status.eq(criteria.status()));
        if (criteria.tradeType() != null) {
            where.and(Expressions.booleanTemplate(
                    "function('find_in_set', {0}, {1}) > 0",
                    criteria.tradeType().name(),
                    item.tradeTypes
            ));
        }
        if (criteria.categoryId() != null) where.and(item.categoryId.eq(criteria.categoryId()));
        if (criteria.createdAfter() != null) where.and(item.createdAt.goe(criteria.createdAfter()));
        if (criteria.createdBefore() != null) where.and(item.createdAt.lt(criteria.createdBefore()));

        OrderSpecifier<?>[] orders = switch (criteria.sort()) {
            case VIEW_DESC -> new OrderSpecifier<?>[]{ item.viewCount.desc(), item.id.desc() };
            case REPORT_DESC, LATEST -> new OrderSpecifier<?>[]{ item.createdAt.desc(), item.id.desc() };
        };

        List<Item> content = queryFactory
                .selectFrom(item).where(where).orderBy(orders)
                .offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();
        Long total = queryFactory.select(item.count()).from(item).where(where).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }
}
