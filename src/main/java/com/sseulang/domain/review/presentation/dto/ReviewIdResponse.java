package com.sseulang.domain.review.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리뷰 작성 후 발급된 id 응답.")
public record ReviewIdResponse(@Schema(example = "9") Long id) { }
