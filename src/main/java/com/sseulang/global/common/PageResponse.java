package com.sseulang.global.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "페이징 응답 — page 는 0-based, size 는 1~100. 채팅 메시지 등 일부 영역은 커서 페이징 사용.")
public record PageResponse<T>(
        @Schema(description = "현재 페이지 항목 리스트") List<T> content,
        @Schema(description = "현재 페이지 번호 (0-based)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 항목 수", example = "123") long totalElements,
        @Schema(description = "전체 페이지 수", example = "7") int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext,
        @Schema(description = "이전 페이지 존재 여부", example = "false") boolean hasPrevious
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }

    public static <T> PageResponse<T> of(
            List<T> content, int page, int size, long totalElements
    ) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages,
                page > 0
        );
    }
}
