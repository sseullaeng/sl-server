package com.sseulang.domain.block.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 차단 Aggregate. {@code user_blocks} 테이블 — UNIQUE(blocker_id, blocked_id),
 * CHECK blocker != blocked.
 */
@Entity
@Table(name = "user_blocks")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static UserBlock create(Long blockerId, Long blockedId) {
        if (blockerId == null || blockerId <= 0) {
            throw new IllegalArgumentException("blockerId 는 양수여야 합니다");
        }
        if (blockedId == null || blockedId <= 0) {
            throw new IllegalArgumentException("blockedId 는 양수여야 합니다");
        }
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("자기 자신은 차단할 수 없습니다");
        }
        UserBlock b = new UserBlock();
        b.blockerId = blockerId;
        b.blockedId = blockedId;
        return b;
    }
}
