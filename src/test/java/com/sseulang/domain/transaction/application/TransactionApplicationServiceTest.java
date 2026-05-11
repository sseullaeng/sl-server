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
import com.sseulang.domain.item.domain.DepositType;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.point.application.InMemoryFakePointHistoryRepository;
import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionApplicationServiceTest {

    private InMemoryFakeTransactionRepository txRepo;
    private InMemoryFakeItemRepository itemRepo;
    private InMemoryFakeUserRepository userRepo;
    private InMemoryFakePointHistoryRepository pointHistoryRepo;
    private com.sseulang.domain.chat.application.InMemoryFakeChatRoomRepository chatRoomRepo;
    private List<Object> publishedEvents;
    private ItemApplicationService itemSvc;
    private TransactionApplicationService service;
    private Long itemId;
    private Long SELLER;
    private Long BUYER;
    private Long OUTSIDER;
    private Long chatRoomId;

    @BeforeEach
    void setUp() {
        txRepo = new InMemoryFakeTransactionRepository();
        itemRepo = new InMemoryFakeItemRepository();
        userRepo = new InMemoryFakeUserRepository();
        pointHistoryRepo = new InMemoryFakePointHistoryRepository();
        publishedEvents = new ArrayList<>();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        UserApplicationService userSvc = new UserApplicationService(
                userRepo,
                new com.sseulang.domain.transaction.application.InMemoryFakeTransactionRepository(),
                new com.sseulang.domain.report.application.InMemoryFakeUserReportRepository(),
                new com.sseulang.domain.auth.application.NoOpRefreshTokenStore(),
                new com.sseulang.domain.auth.application.NoOpEmailSender(),
                java.time.Clock.systemDefaultZone());
        itemSvc = new ItemApplicationService(
                itemRepo, catSvc, userSvc,
                new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(),
                new com.sseulang.domain.item.application.NoOpWishlistView());
        PointApplicationService pointSvc = new PointApplicationService(userSvc, pointHistoryRepo);
        chatRoomRepo = new com.sseulang.domain.chat.application.InMemoryFakeChatRoomRepository();
        com.sseulang.domain.chat.application.ChatRoomApplicationService chatSvc =
                new com.sseulang.domain.chat.application.ChatRoomApplicationService(
                        chatRoomRepo, new com.sseulang.domain.chat.application.InMemoryFakeChatRoomCardRepository(), itemSvc, userSvc, null, null);
        org.springframework.context.ApplicationEventPublisher publisher = publishedEvents::add;
        service = new TransactionApplicationService(
                txRepo, itemSvc, pointSvc, userSvc, chatSvc, publisher, java.time.Clock.systemDefaultZone());

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
        // 라운드 12 (#3.2) — chatRoom 가드. BUYER ↔ SELLER 채팅방 미리 생성.
        com.sseulang.domain.chat.domain.ChatRoom room =
                com.sseulang.domain.chat.domain.ChatRoom.openFor(itemId, BUYER, SELLER);
        chatRoomId = chatRoomRepo.save(room).getId();
    }

    // ───────── create ─────────

    @Test
    @DisplayName("create 정상_status=채팅중")
    void create_정상() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));

        TransactionResult r = service.getById(txId, BUYER);
        assertThat(r.itemId()).isEqualTo(itemId);
        assertThat(r.sellerId()).isEqualTo(SELLER);
        assertThat(r.buyerId()).isEqualTo(BUYER);
        assertThat(r.status()).isEqualTo(TransactionStatus.채팅중);
        assertThat(r.price()).isEqualTo(50_000L);
        assertThat(r.escrowHoldAmount()).isZero();  // 라운드 11 — hold 는 reserve 시점부터
    }

    @Test
    @DisplayName("create dual item 대여 PERCENT deposit_대여가 기준 환산 금액 저장")
    void create_dual_item_대여_percent_deposit_환산() {
        Item rental = itemRepo.save(Item.createMulti(
                SELLER, null, "대여물건", "설명", java.util.EnumSet.of(TradeType.판매, TradeType.대여),
                120_000L, 99_997L, 30L, DepositType.PERCENT, RentalUnit.일, "서울"
        ));
        com.sseulang.domain.chat.domain.ChatRoom room =
                com.sseulang.domain.chat.domain.ChatRoom.openFor(rental.getId(), BUYER, SELLER, TradeType.대여);
        Long roomId = chatRoomRepo.save(room).getId();

        Long txId = service.create(new TransactionCreateCommand(
                rental.getId(), SELLER, roomId,
                java.time.LocalDateTime.now().plusDays(1),
                java.time.LocalDateTime.now().plusDays(2)
        ));

        TransactionResult r = service.getById(txId, BUYER);
        assertThat(r.price()).isEqualTo(99_997L);
        assertThat(r.deposit()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("create buyer 호출_TX_SELLER_ONLY (라운드 12 정책)")
    void create_buyer_거부() {
        assertThatThrownBy(() ->
                service.create(new TransactionCreateCommand(itemId, BUYER, chatRoomId, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TX_SELLER_ONLY);
    }

    @Test
    @DisplayName("create 비활성 Item (예약 상태)_ITEM_INVALID_STATE")
    void create_비활성_거부() {
        Item item = itemRepo.findById(itemId).orElseThrow();
        item.markAsReserved();
        itemRepo.save(item);

        assertThatThrownBy(() ->
                service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_INVALID_STATE);
    }

    @Test
    @DisplayName("create 없는 Item_ITEM_NOT_FOUND")
    void create_없는_item() {
        assertThatThrownBy(() ->
                service.create(new TransactionCreateCommand(9999L, SELLER, chatRoomId, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    // ───────── reserve (hold 흐름) ─────────

    @Test
    @DisplayName("reserve 정상_buyer balance↓ + hold↑ + escrowHoldAmount + 거래보관 history + ReservedEvent")
    void reserve_정상_hold() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));
        userRepo.creditPointBalance(BUYER, 50_000L);  // 잔액 충전

        service.reserve(txId, SELLER);

        TransactionResult r = service.getById(txId, SELLER);
        assertThat(r.status()).isEqualTo(TransactionStatus.예약);
        assertThat(r.escrowHoldAmount()).isEqualTo(50_000L);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.예약);
        assertThat(userRepo.findPointBalance(BUYER)).isZero();
        assertThat(userRepo.findPointHold(BUYER)).isEqualTo(50_000L);
        assertThat(pointHistoryRepo.size()).isEqualTo(1);  // 거래보관 1건
        assertThat(publishedEvents)
                .hasSize(1)
                .first().isInstanceOf(com.sseulang.domain.transaction.domain.event.TransactionReservedEvent.class);
    }

    @Test
    @DisplayName("reserve 잔액 부족_INSUFFICIENT_POINT")
    void reserve_잔액부족_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));
        userRepo.creditPointBalance(BUYER, 10_000L);  // 부족

        assertThatThrownBy(() -> service.reserve(txId, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }

    @Test
    @DisplayName("reserve buyer 호출_TRANSACTION_FORBIDDEN")
    void reserve_buyer_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));

        assertThatThrownBy(() -> service.reserve(txId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("reserve 외부인_TRANSACTION_FORBIDDEN")
    void reserve_외부인_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));

        assertThatThrownBy(() -> service.reserve(txId, OUTSIDER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("두 buyer 동시 거래_첫 reserve 만 성공_두 번째는 TRANSACTION_RESERVED_BY_OTHER")
    void 동시_reserve_차단() {
        Long buyer2 = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-buyer2-" + System.nanoTime(),
                new Email("buyer2-" + System.nanoTime() + "@x.com"), "buyer2", null
        )).getId();
        userRepo.creditPointBalance(BUYER, 50_000L);
        userRepo.creditPointBalance(buyer2, 50_000L);
        // 라운드 12: SELLER 가 두 buyer 와 각 채팅방에서 거래 시작 (두 채팅방 = 두 active 가능).
        com.sseulang.domain.chat.domain.ChatRoom room2 =
                com.sseulang.domain.chat.domain.ChatRoom.openFor(itemId, buyer2, SELLER);
        Long chatRoomId2 = chatRoomRepo.save(room2).getId();
        Long tx1 = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));
        Long tx2 = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId2, null, null));

        service.reserve(tx1, SELLER);

        assertThatThrownBy(() -> service.reserve(tx2, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_RESERVED_BY_OTHER);
    }

    // ───────── markHandover (라운드 11) ─────────

    @Test
    @DisplayName("markHandover seller 정상_status=인계완료 + HandoverEvent")
    void handover_정상() {
        Long txId = reservedTxByBuyer();

        service.markHandover(txId, SELLER);

        TransactionResult r = service.getById(txId, SELLER);
        assertThat(r.status()).isEqualTo(TransactionStatus.인계완료);
        assertThat(r.handoverConfirmedAt()).isNotNull();
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.예약);  // 아직 sold X
        assertThat(userRepo.findPointBalance(BUYER)).isZero();  // hold 유지
        assertThat(userRepo.findPointHold(BUYER)).isEqualTo(50_000L);
        assertThat(eventTypes()).contains("TransactionHandoverConfirmedEvent");
    }

    @Test
    @DisplayName("markHandover buyer 호출_TRANSACTION_HANDOVER_NOT_ALLOWED")
    void handover_buyer_거부() {
        Long txId = reservedTxByBuyer();

        assertThatThrownBy(() -> service.markHandover(txId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_HANDOVER_NOT_ALLOWED);
    }

    @Test
    @DisplayName("markHandover 멱등_이미 인계완료면 no-op")
    void handover_멱등() {
        Long txId = reservedTxByBuyer();
        service.markHandover(txId, SELLER);
        int eventsBefore = publishedEvents.size();

        service.markHandover(txId, SELLER);  // 두 번째 — no-op

        assertThat(service.getById(txId, SELLER).status()).isEqualTo(TransactionStatus.인계완료);
        assertThat(publishedEvents).hasSize(eventsBefore);  // 추가 발행 X
    }

    // ───────── markReceived (라운드 11) ─────────

    @Test
    @DisplayName("markReceived buyer 정상_정산_buyer hold↓ + seller balance↑ + 판매정산 history + ReceiveEvent")
    void receive_정상() {
        Long txId = reservedTxByBuyer();
        service.markHandover(txId, SELLER);

        service.markReceived(txId, BUYER);

        TransactionResult r = service.getById(txId, BUYER);
        assertThat(r.status()).isEqualTo(TransactionStatus.거래완료);
        assertThat(r.receiveConfirmedAt()).isNotNull();
        assertThat(r.completedAt()).isEqualTo(r.receiveConfirmedAt());
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.거래완료);
        assertThat(userRepo.findPointBalance(BUYER)).isZero();
        assertThat(userRepo.findPointHold(BUYER)).isZero();  // 해제됨
        assertThat(userRepo.findPointBalance(SELLER)).isEqualTo(50_000L);
        // history: 거래보관(buyer, reserve) + 판매정산(seller, receive) = 2건
        assertThat(pointHistoryRepo.size()).isEqualTo(2);
        assertThat(eventTypes()).contains("TransactionReceiveConfirmedEvent");
    }

    @Test
    @DisplayName("markReceived seller 호출_TRANSACTION_RECEIVE_NOT_ALLOWED")
    void receive_seller_거부() {
        Long txId = reservedTxByBuyer();
        service.markHandover(txId, SELLER);

        assertThatThrownBy(() -> service.markReceived(txId, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_RECEIVE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("markReceived 인계 전_TRANSACTION_INVALID_STATE")
    void receive_인계전_거부() {
        Long txId = reservedTxByBuyer();

        assertThatThrownBy(() -> service.markReceived(txId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("markReceived 멱등_이미 거래완료면 no-op (재호출 시 더블 정산 X)")
    void receive_멱등() {
        Long txId = reservedTxByBuyer();
        service.markHandover(txId, SELLER);
        service.markReceived(txId, BUYER);
        long sellerBalance = userRepo.findPointBalance(SELLER);
        int historyBefore = pointHistoryRepo.size();

        service.markReceived(txId, BUYER);  // 멱등

        assertThat(userRepo.findPointBalance(SELLER)).isEqualTo(sellerBalance);
        assertThat(pointHistoryRepo.size()).isEqualTo(historyBefore);
    }

    // ───────── cancel 단계별 환불 (라운드 11) ─────────

    @Test
    @DisplayName("cancel 채팅중 buyer_정상_Item 변경 없음 + 잔액 변동 없음")
    void cancel_채팅중() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));

        service.cancel(txId, BUYER, "변심");

        assertThat(service.getById(txId, BUYER).status()).isEqualTo(TransactionStatus.취소);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.판매중);
        assertThat(userRepo.findPointBalance(BUYER)).isZero();
        assertThat(userRepo.findPointHold(BUYER)).isZero();
    }

    @Test
    @DisplayName("cancel 예약 상태_Item 복원 + buyer hold 환불 + 거래환불 history + CancelEvent")
    void cancel_예약_환불() {
        Long txId = reservedTxByBuyer();
        int historyBefore = pointHistoryRepo.size();

        service.cancel(txId, SELLER, "물건 파손");

        assertThat(service.getById(txId, SELLER).status()).isEqualTo(TransactionStatus.취소);
        assertThat(itemRepo.findById(itemId).orElseThrow().getStatus()).isEqualTo(ItemStatus.판매중);
        assertThat(userRepo.findPointBalance(BUYER)).isEqualTo(50_000L);  // 환불
        assertThat(userRepo.findPointHold(BUYER)).isZero();
        assertThat(pointHistoryRepo.size()).isEqualTo(historyBefore + 1);
        assertThat(pointHistoryRepo.findByUserIdOrderByCreatedAtDesc(BUYER))
                .extracting("pointType")
                .contains(PointHistoryType.거래보관, PointHistoryType.거래환불);
        assertThat(eventTypes()).contains("TransactionCanceledEvent");
    }

    @Test
    @DisplayName("cancel 인계완료 이후_TRANSACTION_INVALID_STATE (R2 분쟁 영역)")
    void cancel_인계완료_거부() {
        Long txId = reservedTxByBuyer();
        service.markHandover(txId, SELLER);

        assertThatThrownBy(() -> service.cancel(txId, SELLER, "사후 취소"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("cancel 외부인_TRANSACTION_FORBIDDEN")
    void cancel_외부인_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));

        assertThatThrownBy(() -> service.cancel(txId, OUTSIDER, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("getById 비참여자_TRANSACTION_FORBIDDEN")
    void getById_외부인_거부() {
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));

        assertThatThrownBy(() -> service.getById(txId, OUTSIDER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_FORBIDDEN);
    }

    @Test
    @DisplayName("cancel 후 다른 buyer 가 같은 Item 으로 거래 시작 가능 + hold 환불 받음")
    void cancel_후_새_거래() {
        Long buyer2 = userRepo.save(User.createSocialUser(
                SocialProvider.KAKAO, "k-buyer2-" + System.nanoTime(),
                new Email("buyer2-" + System.nanoTime() + "@x.com"), "buyer2", null
        )).getId();
        userRepo.creditPointBalance(BUYER, 50_000L);
        userRepo.creditPointBalance(buyer2, 50_000L);

        Long tx1 = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));
        service.reserve(tx1, SELLER);
        service.cancel(tx1, SELLER, "재예약 가능");
        assertThat(userRepo.findPointBalance(BUYER)).isEqualTo(50_000L);  // 환불 OK

        // 라운드 12 — buyer2 와 SELLER 의 새 chatRoom 필요 (한 채팅방=1 active 정책 + 거래 시작은 판매자만).
        com.sseulang.domain.chat.domain.ChatRoom room2 =
                com.sseulang.domain.chat.domain.ChatRoom.openFor(itemId, buyer2, SELLER);
        Long chatRoom2Id = chatRoomRepo.save(room2).getId();
        Long tx2 = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoom2Id, null, null));
        service.reserve(tx2, SELLER);

        assertThat(service.getById(tx2, SELLER).status()).isEqualTo(TransactionStatus.예약);
    }

    // ───────── helpers ─────────

    /** buyer 충전 + 거래 생성 + reserve 까지 진행한 trasaction id 반환. */
    private Long reservedTxByBuyer() {
        userRepo.creditPointBalance(BUYER, 50_000L);
        Long txId = service.create(new TransactionCreateCommand(itemId, SELLER, chatRoomId, null, null));
        service.reserve(txId, SELLER);
        return txId;
    }

    private List<String> eventTypes() {
        return publishedEvents.stream().map(e -> e.getClass().getSimpleName()).toList();
    }
}
