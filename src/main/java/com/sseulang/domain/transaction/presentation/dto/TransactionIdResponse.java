package com.sseulang.domain.transaction.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 생성 후 발급된 id 응답.")
public record TransactionIdResponse(@Schema(example = "12") Long id) { }
