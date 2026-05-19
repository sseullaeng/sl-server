package com.sseulang.domain.transaction.application;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransactionCascadeServiceTest {

    private static final Long ITEM_ID = 100L;
    private static final Long SELLER = 10L;
    private static final Long BUYER = 20L;
    private static final Long OUTSIDER = 99L;
    private static final Long CHAT_ROOM = 7L;

    private InMemoryFakeTransactionRepository repo;
    private TransactionCascadeService service;
    private long sequence = 0;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeTransactionRepository();
        // 단위 테스트 — TransactionTemplate.execute 가 실행되도록 PlatformTransactionManager mock.
        // (격리 자체 검증은 별도 IT 영역. 본 테스트는 정책 분기 + 결과 카운트 위주)
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new TransactionCascadeService(repo, txManager, Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("cascade 직거래 활성_거래완료 + paired tx 무영향")
    void cascade_정상() {
        Transaction direct = saveDirect(TradeType.판매, TransactionStatus.예약);
        Transaction paired = savePaired(999L, TransactionStatus.거래완료);

        var result = service.cascadeCompleteByChatRoom(CHAT_ROOM, BUYER, SELLER);

        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.policySkipped()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(repo.findById(direct.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.거래완료);
        assertThat(repo.findById(paired.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.거래완료);
    }

    @Test
    @DisplayName("cascade 대여 직거래_정책 스킵 (반납 흐름 보존)")
    void cascade_대여_제외() {
        Transaction rental = saveDirectWithDeposit(TradeType.대여, TransactionStatus.예약);

        var result = service.cascadeCompleteByChatRoom(CHAT_ROOM, BUYER, SELLER);

        assertThat(result.completed()).isZero();
        assertThat(result.policySkipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(repo.findById(rental.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.예약);
    }

    @Test
    @DisplayName("cascade buyer/seller 불일치_정책 스킵")
    void cascade_권한_불일치() {
        Transaction direct = saveDirect(TradeType.판매, TransactionStatus.채팅중);

        // escrow 의 buyer 가 OUTSIDER (직거래 buyer=BUYER 와 다름)
        var result = service.cascadeCompleteByChatRoom(CHAT_ROOM, OUTSIDER, SELLER);

        assertThat(result.completed()).isZero();
        assertThat(result.policySkipped()).isEqualTo(1);
        assertThat(repo.findById(direct.getId()).orElseThrow().getStatus()).isEqualTo(TransactionStatus.채팅중);
    }

    @Test
    @DisplayName("cascade 후보 없음_no-op")
    void cascade_빈() {
        var result = service.cascadeCompleteByChatRoom(CHAT_ROOM, BUYER, SELLER);
        assertThat(result.completed()).isZero();
        assertThat(result.policySkipped()).isZero();
        assertThat(result.failed()).isZero();
    }

    @Test
    @DisplayName("cascade chatRoomId null_즉시 empty")
    void cascade_null_chatRoom() {
        var result = service.cascadeCompleteByChatRoom(null, BUYER, SELLER);
        assertThat(result.completed()).isZero();
    }

    private Transaction saveDirect(TradeType type, TransactionStatus status) {
        Transaction t = Transaction.create(ITEM_ID, SELLER, BUYER, type, 50_000L, null, null, null, CHAT_ROOM);
        ReflectionTestUtils.setField(t, "id", ++sequence);
        ReflectionTestUtils.setField(t, "status", status);
        repo.save(t);
        return t;
    }

    private Transaction saveDirectWithDeposit(TradeType type, TransactionStatus status) {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(3);
        Transaction t = Transaction.create(ITEM_ID, SELLER, BUYER, type, 5_000L, 10_000L, start, end, CHAT_ROOM);
        ReflectionTestUtils.setField(t, "id", ++sequence);
        ReflectionTestUtils.setField(t, "status", status);
        repo.save(t);
        return t;
    }

    private Transaction savePaired(Long escrowApplicationId, TransactionStatus status) {
        Transaction t = Transaction.create(ITEM_ID, SELLER, BUYER, TradeType.판매, 50_000L, null, null, null, CHAT_ROOM);
        ReflectionTestUtils.setField(t, "id", ++sequence);
        ReflectionTestUtils.setField(t, "escrowApplicationId", escrowApplicationId);
        ReflectionTestUtils.setField(t, "status", status);
        repo.save(t);
        return t;
    }
}
