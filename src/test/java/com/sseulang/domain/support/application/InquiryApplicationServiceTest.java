package com.sseulang.domain.support.application;

import com.sseulang.domain.support.application.dto.InquiryCreateCommand;
import com.sseulang.domain.support.application.dto.InquiryReplyCommand;
import com.sseulang.domain.support.application.dto.InquiryResult;
import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.InquiryStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryApplicationServiceTest {

    private static final Long USER = 5L;
    private static final Long OTHER = 9L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 3, 12, 0);

    private InMemoryFakeInquiryRepository repo;
    private InquiryApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeInquiryRepository();
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        service = new InquiryApplicationService(repo, clock);
    }

    private InquiryCreateCommand cmd() {
        return new InquiryCreateCommand(
                InquiryCategory.결제, "환불 문의", "환불 안 됩니다", "user@x.com", List.of()
        );
    }

    @Test
    @DisplayName("create_PENDING 으로 저장")
    void create_정상() {
        Long id = service.create(USER, cmd());

        InquiryResult r = service.findOwnedById(id, USER);
        assertThat(r.userId()).isEqualTo(USER);
        assertThat(r.status()).isEqualTo(InquiryStatus.PENDING);
        assertThat(r.adminReply()).isNull();
    }

    @Test
    @DisplayName("findOwnedById_타인 접근_INQUIRY_FORBIDDEN")
    void findOwnedById_타인_거부() {
        Long id = service.create(USER, cmd());

        assertThatThrownBy(() -> service.findOwnedById(id, OTHER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_FORBIDDEN);
    }

    @Test
    @DisplayName("deleteByOwner_PENDING_정상 삭제")
    void deleteByOwner_PENDING_정상() {
        Long id = service.create(USER, cmd());

        service.deleteByOwner(id, USER);
        assertThat(repo.size()).isZero();
    }

    @Test
    @DisplayName("deleteByOwner_답변 시작 후_INQUIRY_INVALID_STATE")
    void deleteByOwner_답변시작_거부() {
        Long id = service.create(USER, cmd());
        service.adminChangeStatus(id, InquiryStatus.PROCESSING);

        assertThatThrownBy(() -> service.deleteByOwner(id, USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_INVALID_STATE);
    }

    @Test
    @DisplayName("deleteByOwner_타인_INQUIRY_FORBIDDEN")
    void deleteByOwner_타인_거부() {
        Long id = service.create(USER, cmd());

        assertThatThrownBy(() -> service.deleteByOwner(id, OTHER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_FORBIDDEN);
    }

    @Test
    @DisplayName("adminReply_status null_DONE 으로 자동 + repliedAt 채워짐")
    void adminReply_status_null_DONE() {
        Long id = service.create(USER, cmd());

        service.adminReply(id, new InquiryReplyCommand("3~5일 안에 처리됩니다.", null));

        InquiryResult r = service.adminFindById(id);
        assertThat(r.status()).isEqualTo(InquiryStatus.DONE);
        assertThat(r.adminReply()).isEqualTo("3~5일 안에 처리됩니다.");
        assertThat(r.repliedAt()).isNotNull();
    }

    @Test
    @DisplayName("adminReply_PROCESSING 명시_그 상태로 갱신")
    void adminReply_status_명시() {
        Long id = service.create(USER, cmd());

        service.adminReply(id, new InquiryReplyCommand("확인 중입니다.", InquiryStatus.PROCESSING));

        assertThat(service.adminFindById(id).status()).isEqualTo(InquiryStatus.PROCESSING);
    }

    @Test
    @DisplayName("adminChangeStatus_답변 없이 DONE_도메인 검증 거부 (IllegalArgumentException)")
    void adminChangeStatus_DONE_답변없음_거부() {
        Long id = service.create(USER, cmd());

        assertThatThrownBy(() -> service.adminChangeStatus(id, InquiryStatus.DONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("adminDelete_미존재_INQUIRY_NOT_FOUND")
    void adminDelete_미존재_404() {
        assertThatThrownBy(() -> service.adminDelete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    @DisplayName("findOwnedById_미존재_INQUIRY_NOT_FOUND")
    void findOwnedById_미존재_404() {
        assertThatThrownBy(() -> service.findOwnedById(999L, USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }
}
