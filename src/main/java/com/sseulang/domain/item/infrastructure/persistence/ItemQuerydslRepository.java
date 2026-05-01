package com.sseulang.domain.item.infrastructure.persistence;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Item 동적 검색 — QueryDSL BooleanBuilder.
 *
 * <p>q 검색은 V1 스키마의 {@code FULLTEXT KEY ft_items_title_desc (title, description) WITH PARSER ngram}
 * 인덱스를 사용 (follow-up #10). MySQL ngram_token_size=2 + boolean mode + AND 조합:
 * 입력 토큰 중 길이 ≥ 2 만 사용해 {@code +token1 +token2} 형태로 변환 후
 * {@link com.sseulang.global.config.FullTextFunctionContributor#contributeFunctions Hibernate match_against
 * function} 호출. 토큰 길이 1 이거나 sanitization 후 빈 입력은 LIKE 폴백.</p>
 */
@Repository
public class ItemQuerydslRepository {

    /** ngram_token_size=2 — 1글자 토큰은 매칭 불가. LIKE 로 폴백. */
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
                // 모든 토큰 길이 < 2 → FULLTEXT 매칭 불가. LIKE 폴백.
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

    /**
     * 사용자 입력을 MySQL FULLTEXT boolean mode 쿼리로 변환.
     * <ul>
     *   <li>boolean mode 메타문자 제거 (injection / 의도치 않은 OR 회피)</li>
     *   <li>공백으로 split, 길이 ≥ {@value #MIN_NGRAM_TOKEN_LENGTH} 토큰만 채택</li>
     *   <li>각 토큰에 {@code +} prefix → 모든 토큰 AND 매칭</li>
     * </ul>
     * 결과가 빈 문자열이면 호출자가 LIKE 폴백을 사용해야 함.
     */
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
