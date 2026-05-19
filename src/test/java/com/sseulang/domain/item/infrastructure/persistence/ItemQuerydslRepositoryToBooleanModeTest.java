package com.sseulang.domain.item.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ItemQuerydslRepository#toBooleanModeQuery} 단위 — FULLTEXT boolean mode 쿼리 변환 검증
 * (follow-up #10). package-private static 메서드라 reflection 호출.
 */
class ItemQuerydslRepositoryToBooleanModeTest {

    @Test
    @DisplayName("한글 단일 토큰_+토큰 prefix")
    void single_korean() {
        assertThat(invoke("아이폰")).isEqualTo("+아이폰");
    }

    @Test
    @DisplayName("두 단어 공백 split_각 +prefix AND")
    void two_words() {
        assertThat(invoke("아이폰 미개봉")).isEqualTo("+아이폰 +미개봉");
    }

    @Test
    @DisplayName("길이 1 토큰 제외 (ngram_token_size=2)")
    void short_token_excluded() {
        assertThat(invoke("아 아이폰")).isEqualTo("+아이폰");
    }

    @Test
    @DisplayName("모든 토큰 길이 1_빈 문자열 (LIKE 폴백 신호)")
    void all_short() {
        assertThat(invoke("a b c")).isEmpty();
    }

    @Test
    @DisplayName("boolean mode 메타문자 sanitization (+ - * \" < > ( ) ~ @)")
    void sanitize_meta() {
        assertThat(invoke("+아이폰 -미개봉*")).isEqualTo("+아이폰 +미개봉");
        assertThat(invoke("\"갤럭시\" (S급)")).isEqualTo("+갤럭시 +S급");
        assertThat(invoke("@user~")).isEqualTo("+user");
        assertThat(invoke("@user~50")).isEqualTo("+user +50");  // 메타 분리 후 두 토큰
    }

    @Test
    @DisplayName("연속 공백 split 정상")
    void multi_spaces() {
        assertThat(invoke("아이폰   미개봉")).isEqualTo("+아이폰 +미개봉");
    }

    @Test
    @DisplayName("입력 null/빈/공백_빈 문자열")
    void empty_inputs() {
        assertThat(invoke(null)).isEmpty();
        assertThat(invoke("")).isEmpty();
        assertThat(invoke("   ")).isEmpty();
    }

    @Test
    @DisplayName("영문 + 숫자 혼합")
    void mixed_alphanumeric() {
        assertThat(invoke("iphone 15 pro")).isEqualTo("+iphone +15 +pro");
    }

    private static String invoke(String input) {
        try {
            Method m = ItemQuerydslRepository.class.getDeclaredMethod("toBooleanModeQuery", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, input);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
