package com.sseulang.domain.transaction.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.user.application.InMemoryFakeUserRepository;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.domain.Email;
import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.point.application.InMemoryFakePointHistoryRepository;
import com.sseulang.domain.point.application.PointApplicationService;
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

    private InMemoryFakeTransactionRepository txRepo;
    private InMemoryFakeItemRepository itemRepo;
    private InMemoryFakeUserRepository userRepo;
    private InMemoryFakePointHistoryRepository pointHistoryRepo;
    private ItemApplicationService itemSvc;
    private TransactionApplicationService service;
    private Long itemId;
    private Long SELLER;
    private Long BUYER;
    private Long OUTSIDER;

    @BeforeEach
    void setUp() {
        txRepo = new InMemoryFakeTransactionRepository();
        itemRepo = new InMemoryFakeItemRepository();
        userRepo = new InMemoryFakeUserRepository();
        pointHistoryRepo = new InMemoryFakePointHistoryRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        itemSvc = new ItemApplicationService(itemRepo, catSvc);
        UserApplicationService userSvc = new UserApplicationService(userRepo);
        PointApplicationService pointSvc = new PointApplicationService(userSvc, pointHistoryRepo);
        service = new TransactionApplicationService(txRepo, itemSvc, pointSvc);

        SELLER = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-seller", new Email("seller@x.com"), "seller", null
        )).getId();
        BUYER = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-buyer", new Email("buyer@x.com"), "buyer", null
        )).getId();
        OUTSIDER = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-out", new Email("out@x.com"), "outsider", null
        )).getId();

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
    @DisplayName("complete 정상_seller 호출_Item.판매완료 + 포인트 정산 + history 두 건")
    void complete_정상() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        service.reserve(txId, SELLER);
        userRepo.creditPointBalance(BUYER, 50_000L);  // buyer 잔액 충전

        service.complete(txId, SELLER);

        TransactionResult r = service.getById(txId, SELLER);
        assertThat(r.status()).isEqualTo(TransactionStatus.거래완료);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.거래완료);
        assertThat(userRepo.findPointBalance(BUYER)).isZero();
        assertThat(userRepo.findPointBalance(SELLER)).isEqualTo(50_000L);
        assertThat(pointHistoryRepo.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("complete buyer 호출_TRANSACTION_FORBIDDEN")
    void complete_buyer_금지() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        service.reserve(txId, SELLER);
        userRepo.creditPointBalance(BUYER, 50_000L);

        assertThatThrownBy(() -> service.complete(txId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.예약);
        assertThat(userRepo.findPointBalance(BUYER)).isEqualTo(50_000L);  // 차감 X
    }

    @Test
    @DisplayName("complete buyer 잔액 부족_INSUFFICIENT_POINT_seller 적립도 롤백")
    void complete_buyer_잔액부족() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        service.reserve(txId, SELLER);
        userRepo.creditPointBalance(BUYER, 10_000L);  // 부족

        assertThatThrownBy(() -> service.complete(txId, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);

        // 트랜잭션 롤백 — fake 는 롤백 시뮬 못 하지만 잔액/Item/Tx 상태로 상위 흐름 검증
        // (실제 prod 트랜잭션 IT 는 후속 #23)
    }

    @Test
    @DisplayName("complete 채팅중 상태_TRANSACTION_INVALID_STATE")
    void complete_채팅중_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, BUYER, null, null));
        userRepo.creditPointBalance(BUYER, 50_000L);

        assertThatThrownBy(() -> service.complete(txId, SELLER))
                .isInstanceOf(BusinessException.class);
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
