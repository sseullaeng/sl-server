package com.sseulang.domain.transaction.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionApplicationServiceTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long OUTSIDER = 999L;

    private InMemoryFakeTransactionRepository txRepo;
    private InMemoryFakeItemRepository itemRepo;
    private ItemApplicationService itemSvc;
    private TransactionApplicationService service;
    private Long itemId;

    @BeforeEach
    void setUp() {
        txRepo = new InMemoryFakeTransactionRepository();
        itemRepo = new InMemoryFakeItemRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        itemSvc = new ItemApplicationService(itemRepo, catSvc);
        service = new TransactionApplicationService(txRepo, itemSvc);

        Item item = itemRepo.save(Item.create(
                SELLER, null, "물건", "설명", 50_000L, null, null, TradeType.판매, "서울"
        ));
        itemId = item.getId();
    }

    @Test
    @DisplayName("create 정상_status=채팅중")
    void create_정상() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        TransactionResult r = service.getById(txId, BUYER);
        assertThat(r.itemId()).isEqualTo(itemId);
        assertThat(r.sellerId()).isEqualTo(SELLER);
        assertThat(r.buyerId()).isEqualTo(BUYER);
        assertThat(r.status()).isEqualTo(TransactionStatus.채팅중);
        assertThat(r.price()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("create 본인 물품_TRANSACTION_SELF_NOT_ALLOWED")
    void create_본인_거부() {
        assertThatThrownBy(() ->
                service.create(new TransactionCreateCommand(itemId, SELLER, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_SELF_NOT_ALLOWED);
    }

    @Test
    @DisplayName("create 비활성 Item (예약 상태)_ITEM_INVALID_STATE")
    void create_비활성_거부() {
        Item item = itemRepo.findById(itemId).orElseThrow();
        item.markAsReserved();
        itemRepo.save(item);

        assertThatThrownBy(() ->
                service.create(new TransactionCreateCommand(itemId, BUYER, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_INVALID_STATE);
    }

    @Test
    @DisplayName("create 없는 Item_ITEM_NOT_FOUND")
    void create_없는_item() {
        assertThatThrownBy(() ->
                service.create(new TransactionCreateCommand(9999L, BUYER, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("reserve seller 정상_Transaction.예약 + Item.예약")
    void reserve_정상() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        service.reserve(txId, SELLER);

        assertThat(service.getById(txId, SELLER).status()).isEqualTo(TransactionStatus.예약);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.예약);
    }

    @Test
    @DisplayName("reserve buyer 호출_TRANSACTION_FORBIDDEN")
    void reserve_buyer_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        assertThatThrownBy(() -> service.reserve(txId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("reserve 외부인_TRANSACTION_FORBIDDEN")
    void reserve_외부인_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        assertThatThrownBy(() -> service.reserve(txId, OUTSIDER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("두 buyer 동시 거래_첫 reserve 만 성공_두 번째는 TRANSACTION_RESERVED_BY_OTHER")
    void 동시_reserve_차단() {
        Long buyer2 = 300L;
        Long tx1 = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        Long tx2 = service.create(new TransactionCreateCommand(itemId, buyer2, null, null));

        // 첫 reserve 성공
        service.reserve(tx1, SELLER);

        // 두 번째 reserve 는 Item.status=예약 으로 인해 거부
        assertThatThrownBy(() -> service.reserve(tx2, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_RESERVED_BY_OTHER);
    }

    @Test
    @DisplayName("complete 현재 비활성_TRANSACTION_COMPLETION_UNAVAILABLE")
    void complete_비활성() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        service.reserve(txId, SELLER);

        // Codex 게이트 1 Critical 2 — Day 7/8 결제·포인트 합류 전엔 503 으로 막음.
        // 호출자(권한·상태 무관) 모두 동일 응답.
        assertThatThrownBy(() -> service.complete(txId, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_COMPLETION_UNAVAILABLE);
        assertThatThrownBy(() -> service.complete(txId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_COMPLETION_UNAVAILABLE);

        // Item 은 예약 상태 그대로 유지 — 거래완료 전이 일어나지 않음
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.예약);
    }

    @Test
    @DisplayName("cancel 채팅중 buyer_정상_Item 변경 없음")
    void cancel_채팅중() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        service.cancel(txId, BUYER, "변심");

        assertThat(service.getById(txId, BUYER).status()).isEqualTo(TransactionStatus.취소);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.판매중);
    }

    @Test
    @DisplayName("cancel 예약 상태_Item.판매중 으로 복원")
    void cancel_예약_복원() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        service.reserve(txId, SELLER);

        service.cancel(txId, SELLER, "물건 파손");

        assertThat(service.getById(txId, SELLER).status()).isEqualTo(TransactionStatus.취소);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.판매중);
    }

    @Test
    @DisplayName("cancel 외부인_TRANSACTION_FORBIDDEN")
    void cancel_외부인_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        assertThatThrownBy(() -> service.cancel(txId, OUTSIDER, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("getById 비참여자_TRANSACTION_FORBIDDEN")
    void getById_외부인_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));

        assertThatThrownBy(() -> service.getById(txId, OUTSIDER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("cancel 후 다른 buyer 가 같은 Item 으로 거래 시작 가능")
    void cancel_후_새_거래() {
        Long buyer2 = 300L;
        Long tx1 = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        service.reserve(tx1, SELLER);
        service.cancel(tx1, SELLER, "재예약 가능");

        // Item 이 판매중으로 복원 → 새 거래 시작 OK
        Long tx2 = service.create(new TransactionCreateCommand(itemId, buyer2, null, null));
        service.reserve(tx2, SELLER);

        assertThat(service.getById(tx2, SELLER).status()).isEqualTo(TransactionStatus.예약);
    }
}
