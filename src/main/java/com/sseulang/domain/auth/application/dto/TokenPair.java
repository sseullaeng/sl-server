package com.sseulang.domain.auth.application.dto;

/**
 * Access / Refresh Token 한 쌍. ApplicationService 결과 DTO.
 */
public record TokenPair(String accessToken, String refreshToken) {
}
