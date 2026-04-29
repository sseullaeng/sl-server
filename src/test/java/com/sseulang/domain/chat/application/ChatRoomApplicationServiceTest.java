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
        ItemApplicationService itemSvc = new ItemApplicationService(itemRepo, catSvc);
        service = new ChatRoomApplicationService(roomRepo, itemSvc);

        Item item = itemRepo.save(Item.create(
                SELLER, null, "물건", "설명", 50_000L, null, null, TradeType.판매, null
        ));
        itemId = item.getId();
    }

    @Test
    @DisplayName("openFor 정상_방 생성 + 정규화 user1<user2")
    void openFor_정상() {
        ChatRoomResult r = service.openFor(BUYER, itemId);

        assertThat(r.itemId()).isEqualTo(itemId);
        assertThat(r.user1Id()).isEqualTo(SELLER);  // 100 < 200 → SELLER 가 user1
        assertThat(r.user2Id()).isEqualTo(BUYER);
        assertThat(r.active()).isTrue();
    }

    @Test
    @DisplayName("openFor 멱등_같은 (요청자, item) 두 번 호출_같은 방")
    void openFor_멱등() {
        ChatRoomResult first = service.openFor(BUYER, itemId);
        ChatRoomResult second = service.openFor(BUYER, itemId);

        assertThat(first.id()).isEqualTo(second.id());
    }

    @Test
    @DisplayName("openFor 본인 item_CHAT_FORBIDDEN")
    void openFor_self_거부() {
        assertThatThrownBy(() -> service.openFor(SELLER, itemId))
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

        assertThatThrownBy(() -> service.openFor(BUYER, itemId))
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

        assertThatThrownBy(() -> service.openFor(BUYER, itemId))
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

        // 예약 상태에선 새 채팅 가능 (이미 예약된 buyer 외에도 다른 시점에 채팅 가능 가정)
        ChatRoomResult r = service.openFor(BUYER, itemId);
        assertThat(r).isNotNull();
    }

    @Test
    @DisplayName("getOne 참여자 정상 / 외부인 거부")
    void getOne_권한() {
        Long roomId = service.openFor(BUYER, itemId).id();

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
        Long room1 = service.openFor(BUYER, itemId).id();

        // OTHER 가 SELLER 의 다른 item 으로 방 생성 — BUYER 와 무관
        Item another = itemRepo.save(Item.create(
                SELLER, null, "다른물건", "d", 1L, null, null, TradeType.판매, null
        ));
        Long room2 = service.openFor(OTHER, another.getId()).id();

        Page<ChatRoomResult> mine = service.listMine(BUYER, PageRequest.of(0, 10));

        assertThat(mine.getContent()).extracting(ChatRoomResult::id)
                .containsExactly(room1)
                .doesNotContain(room2);
    }
}
