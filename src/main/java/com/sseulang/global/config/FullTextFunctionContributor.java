package com.sseulang.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL FULLTEXT MATCH AGAINST 를 Hibernate function 으로 등록 (follow-up #10).
 *
 * <p>QueryDSL 에서 {@code Expressions.numberTemplate("function('match_against', ...)")} 로 호출.
 * Boolean mode — 부분 매칭 + 가중치 정렬. ngram 토큰 사이즈 2 (docker-compose / my.cnf 설정 필수).</p>
 *
 * <p>등록은 {@code META-INF/services/org.hibernate.boot.model.FunctionContributor} 파일이 본 클래스를
 * 가리켜야 활성. Spring Boot 가 자동 스캔.</p>
 */
public class FullTextFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
                "match_against",
                "match(?1, ?2) against (?3 in boolean mode)",
                functionContributions.getTypeConfiguration().getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.DOUBLE)
        );
    }
}
