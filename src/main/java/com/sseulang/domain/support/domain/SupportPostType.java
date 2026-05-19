package com.sseulang.domain.support.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "FAQ / QNA")
public enum SupportPostType {
    FAQ,
    QNA
}
