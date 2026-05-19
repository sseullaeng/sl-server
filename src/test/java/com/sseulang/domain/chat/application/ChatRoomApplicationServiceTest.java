package com.sseulang.domain.chat.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.chat.application.dto.ChatRoomResult;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRoomApplicationServiceTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long OTHER = 300L;

    private InMemoryFakeChatRoomRepository roomRepo;
    private InMemoryFakeItemRepository itemRepo;
    private ChatRoomApplicationService service;
    private Long itemId;

    @BeforeEach
    void setUp() {
        roomRepo = new InMemoryFakeChatRoomRepository();
        itemRepo = new InMemoryFakeItemRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        ItemApplicationService itemSvc = new ItemApplicationService(itemRepo, catSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(), new com.sseulang.domain.item.application.NoOpWishlistView(), new com.sseulang.domain.item.application.NoOpItemReportView());
        service = new ChatRoomApplicationService(roomRepo, new com.sseulang.domain.chat.application.InMemoryFakeChatRoomCardRepository(), itemSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.chat.application.NoOpUserView(), new com.sseulang.domain.chat.application.NoOpItemView(), new com.sseulang.domain.chat.application.NoOpTransactionView(), new com.sseulang.domain.chat.application.NoOpEscrowApplicationView());

        Item item = itemRepo.save(Item.create(
                SELLER, null, "물건", "설명", 50_000L, null, null, TradeType.판매, null
        ));
        itemId = item.getId();
    }

    @Test
    @DisplayName("openFor 정상_방 생성 + 정규화 user1<user2")
    void openFor_정상() {
        ChatRoomResult r = service.openFor(BUYER, itemId, null);

        assertThat(r.itemId()).isEqualTo(itemId);
        assertThat(r.user1Id()).isEqualTo(SELLER);  // 100 < 200 → SELLER 가 user1
        assertThat(r.user2Id()).isEqualTo(BUYER);
        assertThat(r.active()).isTrue();
    }

    @Test
    @DisplayName("openFor 멱등_같은 (요청자, item) 두 번 호출_같은 방")
    void openFor_멱등() {
        ChatRoomResult first = service.openFor(BUYER, itemId, null);
        ChatRoomResult second = service.openFor(BUYER, itemId, null);

        assertThat(first.id()).isEqualTo(second.id());
    }

    @Test
    @DisplayName("openFor 나간 방 재호출_기존 방 재진입")
    void openFor_나간방_재진입() {
        Long roomId = service.openFor(BUYER, itemId, null).id();
        service.leave(roomId, BUYER);

        assertThat(service.listMine(BUYER, PageRequest.of(0, 10)).getContent()).isEmpty();

        ChatRoomResult reopened = service.openFor(BUYER, itemId, null);

        assertThat(reopened.id()).isEqualTo(roomId);
        assertThat(reopened.iLeft()).isFalse();
        assertThat(reopened.opponentLeft()).isFalse();
        assertThat(service.listMine(BUYER, PageRequest.of(0, 10)).getContent())
                .extracting(ChatRoomResult::id)
                .containsExactly(roomId);
    }

    @Test
    @DisplayName("reopenForParticipant 기존 roomId 로 나간 방 재진입")
    void reopenForParticipant_나간방_재진입() {
        Long roomId = service.openFor(BUYER, itemId, null).id();
        service.leave(roomId, BUYER);

        ChatRoomApplicationService.ChatRoomMeta meta = service.reopenForParticipant(roomId, BUYER);

        assertThat(meta.chatRoomId()).isEqualTo(roomId);
        assertThat(meta.iLeft()).isFalse();
        assertThat(meta.opponentLeft()).isFalse();
        assertThat(service.listMine(BUYER, PageRequest.of(0, 10)).getContent())
                .extracting(ChatRoomResult::id)
                .containsExactly(roomId);
    }

    @Test
    @DisplayName("openFor 동일 item/user라도 tradeMode가 다르면 별도 방")
    void openFor_동일상대_동일아이템_tradeMode별_분리() {
        Item dual = itemRepo.save(Item.createMulti(
                SELLER, null, "판매대여", "설명",
                java.util.EnumSet.of(TradeType.판매, TradeType.대여),
                80_000L, 10_000L, 30_000L,
                com.sseulang.domain.item.domain.RentalUnit.일,
                null
        ));

        ChatRoomResult sale = service.openFor(BUYER, dual.getId(), TradeType.판매);
        ChatRoomResult rental = service.openFor(BUYER, dual.getId(), TradeType.대여);

        assertThat(sale.id()).isNotEqualTo(rental.id());
        assertThat(service.openFor(BUYER, dual.getId(), TradeType.판매).id()).isEqualTo(sale.id());
        assertThat(service.openFor(BUYER, dual.getId(), TradeType.대여).id()).isEqualTo(rental.id());
    }

    @Test
    @DisplayName("openFor 본인 item_CHAT_FORBIDDEN")
    void openFor_self_거부() {
        assertThatThrownBy(() -> service.openFor(SELLER, itemId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
    }

    @Test
    @DisplayName("openFor 삭제된 item_ITEM_NOT_FOUND")
    void openFor_삭제_item_거부() {
        Item item = itemRepo.findById(itemId).orElseThrow();
        item.markAsDeleted();
        itemRepo.save(item);

        assertThatThrownBy(() -> service.openFor(BUYER, itemId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("openFor 비공개 item_ITEM_INVALID_STATE")
    void openFor_비공개_거부() {
        Item item = itemRepo.findById(itemId).orElseThrow();
        item.markAsHidden();
        itemRepo.save(item);

        assertThatThrownBy(() -> service.openFor(BUYER, itemId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ITEM_INVALID_STATE);
    }

    @Test
    @DisplayName("openFor 예약/거래완료 item_채팅 가능")
    void openFor_예약_허용() {
        Item item = itemRepo.findById(itemId).orElseThrow();
        item.markAsReserved();
        itemRepo.save(item);

        // 라운드 12 PR-C — tradeMode 명시 (null 시 findActiveForTransaction 가드가 예약 차단).
        ChatRoomResult r = service.openFor(BUYER, itemId, com.sseulang.domain.item.domain.TradeType.판매);
        assertThat(r).isNotNull();
    }

    @Test
    @DisplayName("getOne 참여자 정상 / 외부인 거부")
    void getOne_권한() {
        Long roomId = service.openFor(BUYER, itemId, null).id();

        assertThat(service.getOne(roomId, SELLER).id()).isEqualTo(roomId);
        assertThat(service.getOne(roomId, BUYER).id()).isEqualTo(roomId);

        assertThatThrownBy(() -> service.getOne(roomId, OTHER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
    }

    @Test
    @DisplayName("getOne 없는 방_CHAT_ROOM_NOT_FOUND")
    void getOne_없음() {
        assertThatThrownBy(() -> service.getOne(9999L, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("listMine 내가 참여한 방만")
    void listMine() {
        // BUYER 가 SELLER 의 item 으로 방 생성
        Long room1 = service.openFor(BUYER, itemId, null).id();

        // OTHER 가 SELLER 의 다른 item 으로 방 생성 — BUYER 와 무관
        Item another = itemRepo.save(Item.create(
                SELLER, null, "다른물건", "d", 1L, null, null, TradeType.판매, null
        ));
        Long room2 = service.openFor(OTHER, another.getId(), null).id();

        Page<ChatRoomResult> mine = service.listMine(BUYER, PageRequest.of(0, 10));

        assertThat(mine.getContent()).extracting(ChatRoomResult::id)
                .containsExactly(room1)
                .doesNotContain(room2);
    }

    @Test
    @DisplayName("leave 정상_본인 listMine 에서 제외 + 상대방 opponentLeft=true")
    void leave_정상() {
        Long roomId = service.openFor(BUYER, itemId, null).id();

        service.leave(roomId, BUYER);

        // BUYER 측 listMine 에서 제외
        Page<ChatRoomResult> buyerView = service.listMine(BUYER, PageRequest.of(0, 10));
        assertThat(buyerView.getContent()).isEmpty();

        // SELLER 입장에서 상대(BUYER)가 left → opponentLeft=true
        ChatRoomResult sellerView = service.getOne(roomId, SELLER);
        assertThat(sellerView.iLeft()).isFalse();
        assertThat(sellerView.opponentLeft()).isTrue();

        // BUYER 본인 입장에서 iLeft=true (단건 조회는 가능 — soft hide 라 데이터 보존)
        ChatRoomResult buyerOne = service.getOne(roomId, BUYER);
        assertThat(buyerOne.iLeft()).isTrue();
    }

    @Test
    @DisplayName("leave 비참여자_CHAT_FORBIDDEN")
    void leave_비참여자() {
        Long roomId = service.openFor(BUYER, itemId, null).id();

        assertThatThrownBy(() -> service.leave(roomId, OTHER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
    }

    @Test
    @DisplayName("leave 없는 방_CHAT_ROOM_NOT_FOUND")
    void leave_없음() {
        assertThatThrownBy(() -> service.leave(9999L, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("leave idempotent — 두 번 호출해도 OK")
    void leave_idempotent() {
        Long roomId = service.openFor(BUYER, itemId, null).id();
        service.leave(roomId, BUYER);
        // 두 번째 호출은 예외 X
        service.leave(roomId, BUYER);
    }

    @Test
    @DisplayName("requireSendable 본인 left → CHAT_FORBIDDEN")
    void requireSendable_본인left() {
        Long roomId = service.openFor(BUYER, itemId, null).id();
        service.leave(roomId, BUYER);

        assertThatThrownBy(() -> service.requireSendable(roomId, BUYER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
    }

    @Test
    @DisplayName("requireSendable 상대방 left → CHAT_ROOM_OPPONENT_LEFT")
    void requireSendable_상대left() {
        Long roomId = service.openFor(BUYER, itemId, null).id();
        service.leave(roomId, BUYER);

        assertThatThrownBy(() -> service.requireSendable(roomId, SELLER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_OPPONENT_LEFT);
    }

    @Test
    @DisplayName("requireSendable 정상 — 양쪽 모두 안 나간 상태")
    void requireSendable_정상() {
        Long roomId = service.openFor(BUYER, itemId, null).id();
        // throws X
        service.requireSendable(roomId, BUYER);
        service.requireSendable(roomId, SELLER);
    }
}
