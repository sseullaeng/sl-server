package com.sseulang.global.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collections;
import java.util.List;

/**
 * MySQL JSON 컬럼 ↔ {@code List<String>} JPA AttributeConverter.
 *
 * <p>이미지 URL 배열처럼 small bounded list 를 별도 child 테이블 없이 보관하고 싶을 때 사용.
 * autoApply=false — 적용 대상 필드에 명시적으로 {@code @Convert(converter = ...)} 부착.</p>
 *
 * <p>null 또는 빈 리스트는 DB NULL 로 저장. 읽을 때는 빈 리스트로 정규화.</p>
 */
@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("List<String> JSON 직렬화 실패", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> list = MAPPER.readValue(dbData, TYPE);
            return list == null ? Collections.emptyList() : list;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("List<String> JSON 역직렬화 실패: " + dbData, e);
        }
    }
}
