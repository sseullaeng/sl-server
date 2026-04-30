package com.sseulang.domain.withdrawal.application;

import com.sseulang.domain.point.application.InMemoryFakePointHistoryRepository;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import com.sseulang.domain.user.application.InMemoryFakeUserRepository;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalRequestCommand;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalResult;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalApplicationServiceTest {

    private static final long ADMIN = 99L;

    private InMemoryFakeWithdrawalRepository withdrawalRepo;
    private InMemoryFakeUserRepository userRepo;
    private InMemoryFakePointHistoryRepository historyRepo;
    private WithdrawalApplicationService service;
    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        withdrawalRepo = new InMemoryFakeWithdrawalRepository();
        userRepo = new InMemoryFakeUserRepository();
        historyRepo = new InMemoryFakePointHistoryRepository();
        UserApplicationService userSvc = new UserApplicationService(userRepo);
        PointApplicationService pointSvc = new PointApplicationService(userSvc, historyRepo);
        // self 는 REQUIRES_NEW proxy 용 — 단위 테스트엔 Spring 컨텍스트 없으니 자기 자신을 주입.
        // 단위 테스트의 InMemoryFake 는 deadlock/duplicate 던지지 않으므로 catch 경로 미진입.
        service = new WithdrawalApplicationService(withdrawalRepo, pointSvc, userSvc, null);
        ReflectionTestUtils.setField(service, "self", service);

        userId = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-1", new Email("u1@x.com"), "u1", null
        )).getId();
        otherUserId = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-2", new Email("u2@x.com"), "u2", null
        )).getId();
        userRepo.creditPointBalance(userId, 100_000L);  // 잔액 시드
    }

    /** 매 호출마다 새 idempotencyKey 발급 — 멱등 dedup 영향 없는 일반 신청 시나리오용. */
    private WithdrawalRequestCommand cmd(long amount) {
        return cmd(amount, "idem-" + java.util.UUID.randomUUID());
    }

    private WithdrawalRequestCommand cmd(long amount, String idempotencyKey) {
        return new WithdrawalRequestCommand(userId, idempotencyKey, amount, "신한", "110-123-456789", "홍길동");
    }

    // ───────── request ─────────

    @Test
    @DisplayName("request 정상_status=신청 + 잔액 차감 + 출금 history 적재")
    void request_정상() {
        Long id = service.request(cmd(50_000L));

        WithdrawalResult r = service.getById(id, userId);
        assertThat(r.status()).isEqualTo(WithdrawalStatus.신청);
        assertThat(r.amount()).isEqualTo(50_000L);
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(50_000L);  // 100000 - 50000
        assertThat(historyRepo.size()).isEqualTo(1);
        assertThat(historyRepo.all().get(0).getPointType()).isEqualTo(PointHistoryType.출금);
        assertThat(historyRepo.all().get(0).getReferenceType()).isEqualTo(PointReferenceType.WITHDRAWAL);
        assertThat(historyRepo.all().get(0).getReferenceId()).isEqualTo(id);
        assertThat(historyRepo.all().get(0).getAmount()).isEqualTo(-50_000L);
    }

    @Test
    @DisplayName("request 잔액 부족_INSUFFICIENT_POINT_history 미적재")
    void request_잔액부족() {
        assertThatThrownBy(() -> service.request(cmd(150_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_POINT);
        // fake 는 트랜잭션 롤백 시뮬 X — Withdrawal 행은 남을 수 있음 (실 prod IT 에서 정합성 보장)
        // 잔액은 변동 X
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(100_000L);
        assertThat(historyRepo.size()).isZero();
    }

    @Test
    @DisplayName("request amount<=0_INVALID_REQUEST")
    void request_invalid_amount() {
        assertThatThrownBy(() -> service.request(cmd(0L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    // ───────── cancel ─────────

    @Test
    @DisplayName("cancel 정상_status=취소 + 잔액 원복 + 환불 history")
    void cancel_정상() {
        Long id = service.request(cmd(50_000L));

        service.cancel(id, userId);

        assertThat(service.getById(id, userId).status()).isEqualTo(WithdrawalStatus.취소);
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(100_000L);  // 원복
        assertThat(historyRepo.size()).isEqualTo(2);
        assertThat(historyRepo.all().get(1).getPointType()).isEqualTo(PointHistoryType.환불);
        assertThat(historyRepo.all().get(1).getAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("cancel 외부인_WITHDRAWAL_NOT_FOUND (락+권한 동시 검증 → 자원 존재 leak X) + 상태/잔액 변동 X")
    void cancel_외부인() {
        Long id = service.request(cmd(50_000L));

        // 게이트 1 보강: cancel 은 findByIdAndUserIdForUpdate 로 본인 자원만 락+조회.
        // 타인 id 로는 빈 결과 → NOT_FOUND. FORBIDDEN 으로 자원 존재 여부가 새는 것 차단.
        assertThatThrownBy(() -> service.cancel(id, otherUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_NOT_FOUND);
        assertThat(service.getById(id, userId).status()).isEqualTo(WithdrawalStatus.신청);
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(50_000L);
    }

    // ───────── 멱등성 ─────────

    @Test
    @DisplayName("request 동일 idempotencyKey 재요청_같은 id 반환 + 차감/저장 1회 (no-op)")
    void request_idempotent_같은key_재요청() {
        Long id1 = service.request(cmd(30_000L, "idem-X"));
        Long id2 = service.request(cmd(30_000L, "idem-X"));  // 두 번째 호출 — 같은 key

        assertThat(id2).as("같은 key 재요청 → 같은 row id").isEqualTo(id1);
        assertThat(withdrawalRepo.findByUserId(userId, PageRequest.of(0, 10)).getContent())
                .as("withdrawal 행은 1개만").hasSize(1);
        assertThat(historyRepo.size()).as("차감 history 도 1회만").isEqualTo(1);
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(70_000L);  // 100k - 30k 한 번
    }

    @Test
    @DisplayName("request 같은 key + 계좌 필드에 trailing 공백만 다름_dedup 통과 (Command 정규화로 mismatch 거짓 거부 차단)")
    void request_같은key_공백차이_dedup() {
        Long id1 = service.request(new WithdrawalRequestCommand(
                userId, "idem-Z", 10_000L, "신한", "110-123-456789", "홍길동"));

        // 두 번째 요청은 계좌 필드 끝에 공백 — Command compact constructor 가 trim 하므로 같은 값
        Long id2 = service.request(new WithdrawalRequestCommand(
                userId, "idem-Z", 10_000L, "신한 ", "110-123-456789  ", "홍길동 "));

        assertThat(id2).as("공백만 다른 payload 는 정규화 후 동일 → dedup").isEqualTo(id1);
        assertThat(historyRepo.size()).as("차감 1회만").isEqualTo(1);
    }

    @Test
    @DisplayName("request 같은 key + 다른 payload_WITHDRAWAL_IDEMPOTENCY_MISMATCH (silent pass 차단)")
    void request_같은key_다른payload_거부() {
        Long id1 = service.request(cmd(30_000L, "idem-Y"));

        // 같은 key 인데 amount 가 다름 → 클라이언트 버그 가능성, 명시 거부
        assertThatThrownBy(() -> service.request(
                new WithdrawalRequestCommand(userId, "idem-Y", 50_000L, "신한", "110-123-456789", "홍길동")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_IDEMPOTENCY_MISMATCH);

        // 잔액/withdrawal 행은 첫 호출 그대로
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("request 같은 key + 다른 사용자_각자 별개 row (격리)")
    void request_같은key_다른사용자_별개() {
        userRepo.creditPointBalance(otherUserId, 100_000L);

        Long id1 = service.request(new WithdrawalRequestCommand(userId, "shared", 10_000L, "신한", "1", "홍"));
        Long id2 = service.request(new WithdrawalRequestCommand(otherUserId, "shared", 10_000L, "신한", "1", "홍"));

        assertThat(id1).isNotEqualTo(id2);
        assertThat(historyRepo.size()).isEqualTo(2);  // 각자 차감
    }

    @Test
    @DisplayName("cancel 승인 상태_WITHDRAWAL_NOT_CANCELABLE")
    void cancel_승인_거부() {
        Long id = service.request(cmd(50_000L));
        service.adminApprove(id, ADMIN, null);

        assertThatThrownBy(() -> service.cancel(id, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_NOT_CANCELABLE);
    }

    // ───────── adminApprove ─────────

    @Test
    @DisplayName("adminApprove 정상_status=승인 + 잔액 변동 X")
    void adminApprove_정상() {
        Long id = service.request(cmd(50_000L));

        service.adminApprove(id, ADMIN, "확인 완료");

        WithdrawalResult r = service.getById(id, userId);
        assertThat(r.status()).isEqualTo(WithdrawalStatus.승인);
        assertThat(r.adminId()).isEqualTo(ADMIN);
        assertThat(r.adminMemo()).isEqualTo("확인 완료");
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(50_000L);  // 신청 시점 차감 그대로
    }

    @Test
    @DisplayName("adminApprove 신청 외 상태_WITHDRAWAL_INVALID_STATE")
    void adminApprove_상태_거부() {
        Long id = service.request(cmd(50_000L));
        service.adminApprove(id, ADMIN, null);

        assertThatThrownBy(() -> service.adminApprove(id, ADMIN, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);
    }

    // ───────── adminReject ─────────

    @Test
    @DisplayName("adminReject 정상_status=거부 + 잔액 원복")
    void adminReject_정상() {
        Long id = service.request(cmd(50_000L));

        service.adminReject(id, ADMIN, "계좌 오류");

        WithdrawalResult r = service.getById(id, userId);
        assertThat(r.status()).isEqualTo(WithdrawalStatus.거부);
        assertThat(r.adminMemo()).isEqualTo("계좌 오류");
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(100_000L);  // 원복
        assertThat(historyRepo.size()).isEqualTo(2);
    }

    // ───────── adminComplete ─────────

    @Test
    @DisplayName("adminComplete 승인 → 완료")
    void adminComplete_정상() {
        Long id = service.request(cmd(50_000L));
        service.adminApprove(id, ADMIN, null);

        service.adminComplete(id, ADMIN);

        assertThat(service.getById(id, userId).status()).isEqualTo(WithdrawalStatus.완료);
        // 외부 이체 mock — 잔액 변동 없음 (신청 시점 차감 유지)
        assertThat(userRepo.findPointBalance(userId)).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("adminComplete 신청 상태_WITHDRAWAL_INVALID_STATE")
    void adminComplete_상태_거부() {
        Long id = service.request(cmd(50_000L));

        assertThatThrownBy(() -> service.adminComplete(id, ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);
    }

    // ───────── 조회 ─────────

    @Test
    @DisplayName("getById 외부인_WITHDRAWAL_FORBIDDEN")
    void getById_외부인() {
        Long id = service.request(cmd(50_000L));

        assertThatThrownBy(() -> service.getById(id, otherUserId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_FORBIDDEN);
    }

    @Test
    @DisplayName("findMyWithdrawals 페이징")
    void findMyWithdrawals() {
        service.request(cmd(10_000L));
        service.request(cmd(20_000L));

        Page<WithdrawalResult> page = service.findMyWithdrawals(userId, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("adminFindByStatus 신청만 필터")
    void adminFindByStatus() {
        Long id1 = service.request(cmd(10_000L));
        Long id2 = service.request(cmd(20_000L));
        service.adminApprove(id2, ADMIN, null);

        Page<WithdrawalResult> 신청 = service.adminFindByStatus(WithdrawalStatus.신청, PageRequest.of(0, 10));
        assertThat(신청.getContent()).hasSize(1);
        assertThat(신청.getContent().get(0).id()).isEqualTo(id1);

        Page<WithdrawalResult> 전체 = service.adminFindByStatus(null, PageRequest.of(0, 10));
        assertThat(전체.getContent()).hasSize(2);
    }
}
