package com.sseulang.domain.block.application;

import com.sseulang.domain.block.application.dto.UserBlockResult;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserBlockApplicationServiceTest {

    private InMemoryFakeUserBlockRepository repo;
    private UserBlockApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeUserBlockRepository();
        service = new UserBlockApplicationService(repo);
    }

    @Test
    @DisplayName("block 정상")
    void block_정상() {
        service.block(1L, 2L);
        assertThat(repo.existsByBlockerIdAndBlockedId(1L, 2L)).isTrue();
    }

    @Test
    @DisplayName("block 자기 차단_INVALID_REQUEST")
    void block_self_거부() {
        assertThatThrownBy(() -> service.block(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("block 중복_idempotent")
    void block_중복() {
        service.block(1L, 2L);
        service.block(1L, 2L);
        service.block(1L, 2L);
        assertThat(repo.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("unblock 정상")
    void unblock_정상() {
        service.block(1L, 2L);
        service.unblock(1L, 2L);
        assertThat(repo.existsByBlockerIdAndBlockedId(1L, 2L)).isFalse();
    }

    @Test
    @DisplayName("unblock 없는 항목_idempotent")
    void unblock_없음() {
        service.unblock(1L, 2L);
        assertThat(repo.size()).isZero();
    }

    @Test
    @DisplayName("listMine 본인 차단만")
    void listMine() {
        service.block(1L, 2L);
        service.block(1L, 3L);
        service.block(99L, 4L);  // 다른 사람 차단

        Page<UserBlockResult> mine = service.listMine(1L, PageRequest.of(0, 10));
        assertThat(mine.getTotalElements()).isEqualTo(2);
        assertThat(mine.getContent()).extracting(UserBlockResult::blockedId)
                .containsExactlyInAnyOrder(2L, 3L);
    }
}
