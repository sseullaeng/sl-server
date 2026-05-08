package com.sseulang.domain.review.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.point.application.InMemoryFakePointHistoryRepository;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.review.application.dto.ReviewWriteCommand;
import com.sseulang.domain.transaction.application.InMemoryFakeTransactionRepository;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.user.application.InMemoryFakeUserRepository;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewApplicationServiceTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long OUTSIDER = 999L;

    private InMemoryFakeReviewRepository reviewRepo;
    private InMemoryFakeTransactionRepository txRepo;
    private InMemoryFakeUserRepository userRepo;
    private ReviewApplicationService service;
    private Long completedTxId;

    @BeforeEach
    void setUp() {
        reviewRepo = new InMemoryFakeReviewRepository();
        txRepo = new InMemoryFakeTransactionRepository();
        userRepo = new InMemoryFakeUserRepository();
        InMemoryFakeItemRepository itemRepo = new InMemoryFakeItemRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        UserApplicationService userSvc = new UserApplicationService(userRepo, new com.sseulang.domain.transaction.application.InMemoryFakeTransactionRepository(), new com.sseulang.domain.report.application.InMemoryFakeUserReportRepository(), new com.sseulang.domain.auth.application.NoOpRefreshTokenStore(), java.time.Clock.systemDefaultZone());
        ItemApplicationService itemSvc = new ItemApplicationService(itemRepo, catSvc, userSvc, new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(), new com.sseulang.domain.item.application.NoOpWishlistView());
        PointApplicationService pointSvc = new PointApplicationService(userSvc, new InMemoryFakePointHistoryRepository());
        TransactionApplicationService txSvc = new TransactionApplicationService(
                txRepo, itemSvc, pointSvc, userSvc,
                (org.springframework.context.ApplicationEventPublisher) event -> {},
                java.time.Clock.systemDefaultZone());
        service = new ReviewApplicationService(reviewRepo, txSvc, userSvc);

        Item item = itemRepo.save(Item.create(
                SELLER, null, "물건", "설명", 50_000L, null, null, TradeType.판매, null
        ));
        completedTxId = persistCompletedTransaction(item.getId(), LocalDateTime.now().minusDays(1));
    }

    @Test
    @DisplayName("write 정상_review 저장 + trust_score 갱신 호출")
    void write_정상() {
        Long reviewId = service.write(new ReviewWriteCommand(completedTxId, BUYER, 5, "친절"));

        assertThat(reviewId).isNotNull();
        assertThat(userRepo.recomputeCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("write 거래중인 tx_TRANSACTION_INVALID_STATE")
    void write_거래중_거부() {
        Long ongoingTxId = persistTransaction(1L, TransactionStatus.예약, null);

        assertThatThrownBy(() -> service.write(new ReviewWriteCommand(ongoingTxId, BUYER, 5, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("write 비참여자_TRANSACTION_FORBIDDEN")
    void write_비참여자_거부() {
        assertThatThrownBy(() -> service.write(new ReviewWriteCommand(completedTxId, OUTSIDER, 5, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("write 7일 초과_REVIEW_PERIOD_EXPIRED")
    void write_7일_초과_거부() {
        Long oldTxId = persistCompletedTransaction(1L, LocalDateTime.now().minusDays(8));

        assertThatThrownBy(() -> service.write(new ReviewWriteCommand(oldTxId, BUYER, 5, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_PERIOD_EXPIRED);
    }

    @Test
    @DisplayName("write 7일 정확히 경계_허용")
    void write_7일_경계_허용() {
        Long sevenDaysTxId = persistCompletedTransaction(1L, LocalDateTime.now().minusDays(7).plusMinutes(1));

        Long reviewId = service.write(new ReviewWriteCommand(sevenDaysTxId, BUYER, 5, null));
        assertThat(reviewId).isNotNull();
    }

    @Test
    @DisplayName("write 양방향_seller / buyer 모두 작성 가능")
    void write_양방향() {
        Long buyerReview = service.write(new ReviewWriteCommand(completedTxId, BUYER, 5, "buyer 측"));
        Long sellerReview = service.write(new ReviewWriteCommand(completedTxId, SELLER, 4, "seller 측"));

        assertThat(buyerReview).isNotEqualTo(sellerReview);
        // trust_score recompute 가 양쪽 모두 호출됨 (BUYER 가 SELLER 평가, SELLER 가 BUYER 평가)
        assertThat(userRepo.recomputeCallCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("write 없는 tx_TRANSACTION_NOT_FOUND")
    void write_없는_tx() {
        assertThatThrownBy(() -> service.write(new ReviewWriteCommand(9999L, BUYER, 5, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
    }

    @Test
    @DisplayName("listPending 본인 미작성 거래완료_포함 / 작성한 거래는 제외 (follow-up #56)")
    void listPending_미작성_포함() {
        // setUp 의 completedTxId 는 BUYER 입장에서 미작성 → 포함
        // SELLER 가 작성한 별도 거래 시드 후 SELLER 입장에서는 제외되어야 함
        Long anotherTxId = persistCompletedTransaction(1L, LocalDateTime.now().minusDays(2));
        service.write(new ReviewWriteCommand(anotherTxId, SELLER, 4, null));
        // SELLER 가 anotherTxId 에 대해 작성했음을 fake repo 에 알림 (NOT EXISTS 시뮬레이션)
        txRepo.markReviewed(anotherTxId, SELLER);

        org.springframework.data.domain.Page<com.sseulang.domain.transaction.application.dto.PendingReviewableResult> sellerPending =
                service.listPending(SELLER, org.springframework.data.domain.PageRequest.of(0, 20));

        // SELLER 는 anotherTxId 에 대해 이미 작성 → 제외. completedTxId 는 미작성 → 포함.
        assertThat(sellerPending.getContent()).extracting("transactionId")
                .containsExactlyInAnyOrder(completedTxId);
        // 상대방(reviewee) 는 BUYER
        assertThat(sellerPending.getContent().get(0).revieweeId()).isEqualTo(BUYER);
    }

    @Test
    @DisplayName("listPending 7일 초과 거래는 제외")
    void listPending_7일_초과_제외() {
        Long oldTxId = persistCompletedTransaction(1L, LocalDateTime.now().minusDays(8));

        org.springframework.data.domain.Page<com.sseulang.domain.transaction.application.dto.PendingReviewableResult> pending =
                service.listPending(BUYER, org.springframework.data.domain.PageRequest.of(0, 20));

        // 7일 이내인 completedTxId 만 포함 — oldTxId 는 since 필터로 제외
        assertThat(pending.getContent()).extracting("transactionId").doesNotContain(oldTxId);
        assertThat(pending.getContent()).extracting("transactionId").contains(completedTxId);
    }

    @Test
    @DisplayName("listPending deadline = completedAt + 7d")
    void listPending_deadline_정확() {
        org.springframework.data.domain.Page<com.sseulang.domain.transaction.application.dto.PendingReviewableResult> pending =
                service.listPending(BUYER, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(pending.getContent()).hasSize(1);
        com.sseulang.domain.transaction.application.dto.PendingReviewableResult r = pending.getContent().get(0);
        assertThat(r.deadline()).isEqualTo(r.completedAt().plusDays(7));
    }

    private Long persistCompletedTransaction(Long itemId, LocalDateTime completedAt) {
        return persistTransaction(itemId, TransactionStatus.거래완료, completedAt);
    }

    private Long persistTransaction(Long itemId, TransactionStatus status, LocalDateTime completedAt) {
        Transaction tx = Transaction.create(itemId, SELLER, BUYER, TradeType.판매, 50_000L, null, null, null);
        ReflectionTestUtils.setField(tx, "status", status);
        if (completedAt != null) {
            ReflectionTestUtils.setField(tx, "completedAt", completedAt);
        }
        return txRepo.save(tx).getId();
    }
}
