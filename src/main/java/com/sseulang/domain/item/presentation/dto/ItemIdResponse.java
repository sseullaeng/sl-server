package com.sseulang.domain.item.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "물품 등록 후 발급된 id 응답.")
public record ItemIdResponse(@Schema(example = "42") Long id) { }
