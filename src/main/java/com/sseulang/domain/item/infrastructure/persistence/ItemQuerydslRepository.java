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
        if (criteria.sellerId() != null) {
            where.and(item.sellerId.eq(criteria.sellerId()));
        }

        List<Item> content = queryFactory
                .selectFrom(item)
                .where(where)
                .orderBy(orderBy(criteria.sort(), item))
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

    

    private static OrderSpecifier<?>[] orderBy(ItemSort sort, QItem item) {
        return switch (sort) {
            case PRICE_ASC -> new OrderSpecifier<?>[]{ item.price.asc(), item.id.desc() };
            case PRICE_DESC -> new OrderSpecifier<?>[]{ item.price.desc(), item.id.desc() };
            case VIEW_DESC -> new OrderSpecifier<?>[]{ item.viewCount.desc(), item.createdAt.desc(), item.id.desc() };
            case WISHLIST_DESC -> new OrderSpecifier<?>[]{ item.wishlistCount.desc(), item.createdAt.desc(), item.id.desc() };
            case LATEST -> new OrderSpecifier<?>[]{ item.createdAt.desc(), item.id.desc() };
        };
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
}
