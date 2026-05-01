package com.sseulang.domain.delivery.domain;

/** Delivery status 별 카운트 집계 row (admin stats 용). */
public record DeliveryStatusCount(DeliveryStatus status, long count) {}
