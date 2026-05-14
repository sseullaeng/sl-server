package com.sseulang.domain.overdue.presentation.dto;

import com.sseulang.domain.overdue.domain.OverdueLegalAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자 법적 조치 단계 전이 요청. NONE 은 허용 X.")
public record OverdueLegalActionRequest(
        @Schema(description = "법적 조치 액션", example = "내용증명",
                allowableValues = {"내용증명", "분쟁조정", "소송제기"})
        @NotNull OverdueLegalAction action
) { }
