package com.sseulang.domain.point.application;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.InMemoryFakeUserRepository;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PointApplicationService 단위 테스트 — 가이드 §4.8 / §5.3 / CLAUDE.md §7.6 (잔액 변경 단위 테스트 의무).
 *
 * <p>fake 라 prod 락 의미 검증 X (실 MySQL 락 / 동시성 race 는 후속 IT 이슈 #23 에서).
 * 본 테스트는 흐름 단위 (잔액 변동 + history 누락 방지 + id-asc deadlock 방지 순서) 만 가드.</p>
 */
class PointApplicationServiceTest {

    private InMemoryFakeUserRepository userRepo;
    private InMemoryFakePointHistoryRepository historyRepo;
    private PointApplicationService service;
    private Long buyerId;
    private Long sellerId;

    @BeforeEach
    void setUp() {
        userRepo = new InMemoryFakeUserRepository();
        historyRepo = new InMemoryFakePointHistoryRepository();
        UserApplicationService userSvc = new UserApplicationService(userRepo, new com.sseulang.domain.transaction.application.InMemoryFakeTransactionRepository(), new com.sseulang.domain.report.application.InMemoryFakeUserReportRepository(), new com.sseulang.domain.auth.application.NoOpRefreshTokenStore(), java.time.Clock.systemDefaultZone());
        service = new PointApplicationService(userSvc, historyRepo);

        buyerId = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-buyer", new Email("buyer@x.com"), "buyer", null
        )).getId();
        sellerId = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-seller", new Email("seller@x.com"), "seller", null
        )).getId();
    }

    // ───────── credit ─────────

    @Test
    @DisplayName("credit 정상_잔액 증가 + history 적재 (충전 / refType=PAYMENT)")
    void credit_정상() {
        service.credit(buyerId, 50_000L, PointHistoryType.충전, PointReferenceType.PAYMENT, 1L, "토스 충전");

        assertThat(userRepo.findPointBalance(buyerId)).isEqualTo(50_000L);
        assertThat(historyRepo.size()).isEqualTo(1);
        PointHistory h = historyRepo.all().get(0);
        assertThat(h.getUserId()).isEqualTo(buyerId);
        assertThat(h.getPointType()).isEqualTo(PointHistoryType.충전);
        assertThat(h.getAmount()).isEqualTo(50_000L);
        assertThat(h.getBalanceAfter()).isEqualTo(50_000L);
        assertThat(h.getReferenceType()).isEqualTo(PointReferenceType.PAYMENT);
        assertThat(h.getReferenceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("credit 미존재 userId_USER_NOT_FOUND_history 미적재")
    void credit_미존재() {
        assertThatThrownBy(() -> service.credit(
                999L, 50_000L, PointHistoryType.충전, PointReferenceType.PAYMENT, 1L, null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(historyRepo.size()).isZero();
    }

    // ───────── deduct ─────────

    @Test
    @DisplayName("deduct 정상_잔액 감소 + history 적재 (음수 amount)")
    void deduct_정상() {
        userRepo.creditPointBalance(buyerId, 50_000L);

        service.deduct(buyerId, 30_000L, PointHistoryType.결제, PointReferenceType.TRANSACTION, 7L, "거래 결제");

        assertThat(userRepo.findPointBalance(buyerId)).isEqualTo(20_000L);
        PointHistory h = historyRepo.all().get(0);
        assertThat(h.getPointType()).isEqualTo(PointHistoryType.결제);
        assertThat(h.getAmount()).isEqualTo(-30_000L);
        assertThat(h.getBalanceAfter()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("deduct 잔액 부족_INSUFFICIENT_POINT_history 미적재")
    void deduct_잔액부족() {
        userRepo.creditPointBalance(buyerId, 10_000L);

        assertThatThrownBy(() -> service.deduct(
                buyerId, 30_000L, PointHistoryType.결제, PointReferenceType.TRANSACTION, 7L, null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);
        assertThat(userRepo.findPointBalance(buyerId)).isEqualTo(10_000L);  // 그대로
        assertThat(historyRepo.size()).isZero();
    }

    // ───────── transfer (거래 정산) ─────────

    @Test
    @DisplayName("transfer 정상_buyer 차감 + seller 적립 + history 두 건 (결제/판매정산)")
    void transfer_정상() {
        userRepo.creditPointBalance(buyerId, 50_000L);

        service.transfer(buyerId, sellerId, 50_000L, PointReferenceType.TRANSACTION, 7L, "거래 결제");

        assertThat(userRepo.findPointBalance(buyerId)).isZero();
        assertThat(userRepo.findPointBalance(sellerId)).isEqualTo(50_000L);
        assertThat(historyRepo.size()).isEqualTo(2);
        // 두 건: 결제(-) + 판매정산(+)
        assertThat(historyRepo.all().stream().anyMatch(h -> h.getPointType() == PointHistoryType.결제 && h.getAmount() == -50_000L)).isTrue();
        assertThat(historyRepo.all().stream().anyMatch(h -> h.getPointType() == PointHistoryType.판매정산 && h.getAmount() == 50_000L)).isTrue();
    }

    @Test
    @DisplayName("transfer 잔액 부족_INSUFFICIENT_POINT_seller 적립도 함께 롤백 (실은 fake 라 검증 한계 — 흐름만 가드)")
    void transfer_잔액부족() {
        userRepo.creditPointBalance(buyerId, 10_000L);

        assertThatThrownBy(() -> service.transfer(
                buyerId, sellerId, 50_000L, PointReferenceType.TRANSACTION, 7L, null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);
        // fake 는 트랜잭션 롤백 시뮬 X — 이 시점 buyer 잔액은 그대로, seller 가 먼저 적립됐을 수 있음 (id-asc 순서 영향).
        // 실 prod 트랜잭션 IT 는 후속 #23 에서 검증.
    }

    @Test
    @DisplayName("transfer buyerId<sellerId_buyer(deduct) 먼저 호출 → seller(credit)")
    void transfer_id_asc_buyer_먼저() {
        // buyerId(1) < sellerId(2) → deduct(buyer) 먼저, credit(seller) 나중
        userRepo.creditPointBalance(buyerId, 50_000L);
        assertThat(buyerId).isLessThan(sellerId);

        service.transfer(buyerId, sellerId, 50_000L, PointReferenceType.TRANSACTION, 7L, null);

        // 적재 순서로 락 순서 확인 (history 의 createdAt 은 동일 시각이라 시퀀스 ID 활용)
        PointHistory first = historyRepo.all().get(0);
        PointHistory second = historyRepo.all().get(1);
        assertThat(first.getUserId()).isEqualTo(buyerId);
        assertThat(first.getPointType()).isEqualTo(PointHistoryType.결제);
        assertThat(second.getUserId()).isEqualTo(sellerId);
        assertThat(second.getPointType()).isEqualTo(PointHistoryType.판매정산);
    }

    @Test
    @DisplayName("transfer sellerId<buyerId_seller(credit) 먼저 호출 → buyer(deduct) (역방향 락 순서)")
    void transfer_id_asc_seller_먼저() {
        // 추가 user 두 개 더 만들어서 sellerId < buyerId 시나리오 구성
        Long highBuyer = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-high-buy", new Email("hb@x.com"), "hb", null
        )).getId();
        Long lowSeller = sellerId;  // 기존 sellerId(2) 가 highBuyer(3) 보다 작음
        userRepo.creditPointBalance(highBuyer, 50_000L);

        service.transfer(highBuyer, lowSeller, 50_000L, PointReferenceType.TRANSACTION, 7L, null);

        PointHistory first = historyRepo.all().get(0);
        PointHistory second = historyRepo.all().get(1);
        // sellerId(2) < highBuyer(3) — credit(seller) 먼저, deduct(buyer) 나중
        assertThat(first.getUserId()).isEqualTo(lowSeller);
        assertThat(first.getPointType()).isEqualTo(PointHistoryType.판매정산);
        assertThat(second.getUserId()).isEqualTo(highBuyer);
        assertThat(second.getPointType()).isEqualTo(PointHistoryType.결제);
    }

    @Test
    @DisplayName("transfer buyer==seller_IllegalArgumentException")
    void transfer_self() {
        assertThatThrownBy(() -> service.transfer(
                buyerId, buyerId, 1_000L, PointReferenceType.TRANSACTION, 1L, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transfer amount<=0_IllegalArgumentException")
    void transfer_invalid_amount() {
        assertThatThrownBy(() -> service.transfer(
                buyerId, sellerId, 0L, PointReferenceType.TRANSACTION, 1L, null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transfer(
                buyerId, sellerId, -1L, PointReferenceType.TRANSACTION, 1L, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    // ───────── refund (거래 환불 — 양쪽 원복) ─────────

    @Test
    @DisplayName("refund 정상_buyer 적립 + seller 차감 + 환불 history 두 건")
    void refund_정상() {
        // 정산 끝난 상태로 셋업 — buyer 0, seller 50000
        userRepo.creditPointBalance(sellerId, 50_000L);

        service.refund(buyerId, sellerId, 50_000L, PointReferenceType.TRANSACTION, 7L, "거래 취소 환불");

        assertThat(userRepo.findPointBalance(buyerId)).isEqualTo(50_000L);
        assertThat(userRepo.findPointBalance(sellerId)).isZero();
        assertThat(historyRepo.size()).isEqualTo(2);
        assertThat(historyRepo.all()).allMatch(h -> h.getPointType() == PointHistoryType.환불);
    }

    @Test
    @DisplayName("refund seller 잔액 부족 (이미 사용)_INSUFFICIENT_POINT")
    void refund_seller_잔액부족() {
        // seller 가 이미 다른 거래로 잔액 사용 — 환불 불가 → 운영 escalation
        userRepo.creditPointBalance(sellerId, 10_000L);

        assertThatThrownBy(() -> service.refund(
                buyerId, sellerId, 50_000L, PointReferenceType.TRANSACTION, 7L, null
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }
}
