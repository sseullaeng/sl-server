package com.sseulang.domain.item.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter(autoApply = false)
public class TradeTypesConverter implements AttributeConverter<Set<TradeType>, String> {

    @Override
    public String convertToDatabaseColumn(Set<TradeType> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<TradeType> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return EnumSet.noneOf(TradeType.class);
        }
        Set<TradeType> result = new LinkedHashSet<>();
        for (String token : dbData.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            result.add(TradeType.valueOf(t));
        }
        return result;
    }
}
