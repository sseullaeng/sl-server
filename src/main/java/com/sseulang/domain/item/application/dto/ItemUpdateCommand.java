package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.RentalUnit;

import java.util.List;

/**
 * 물품 수정 커맨드. 컬렉션 필드({@code imageUrls}, {@code hashtags})는 {@code null} 이면 변경 없음 —
 * non-null 이면 전체 교체. {@code categoryId} 동일 정책.
 */
public record ItemUpdateCommand(
        Long categoryId,
        String title,
        String description,
        long price,
        Long deposit,
        RentalUnit rentalUnit,
        String region,
        List<String> imageUrls,
        List<String> hashtags
) { }
