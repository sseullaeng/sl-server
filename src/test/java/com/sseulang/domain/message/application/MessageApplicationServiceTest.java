package com.sseulang.domain.message.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.application.InMemoryFakeCategoryRepository;
import com.sseulang.domain.chat.application.ChatRoomApplicationService;
import com.sseulang.domain.chat.application.InMemoryFakeChatRoomRepository;
import com.sseulang.domain.chat.domain.ChatRoom;
import com.sseulang.domain.item.application.InMemoryFakeItemRepository;
import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.message.application.dto.MessageResult;
import com.sseulang.domain.message.application.dto.MessageSendCommand;
import com.sseulang.domain.message.application.event.ChatRealtimePublishRequestedEvent;
import com.sseulang.domain.notification.application.InMemoryFakeNotificationRepository;
import com.sseulang.domain.notification.application.NotificationApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.websocket.FakeRealtimePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageApplicationServiceTest {

    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final Long OUTSIDER = 999L;

    private InMemoryFakeMessageRepository msgRepo;
    private InMemoryFakeChatRoomRepository roomRepo;
    private InMemoryFakeNotificationRepository notifRepo;
    private FakeRealtimePublisher publisher;
    private MessageApplicationService service;
    private Long roomId;

    @BeforeEach
    void setUp() {
        msgRepo = new InMemoryFakeMessageRepository();
        roomRepo = new InMemoryFakeChatRoomRepository();
        notifRepo = new InMemoryFakeNotificationRepository();
        publisher = new FakeRealtimePublisher();
        InMemoryFakeItemRepository itemRepo = new InMemoryFakeItemRepository();
        CategoryApplicationService catSvc = new CategoryApplicationService(new InMemoryFakeCategoryRepository());
        ItemApplicationService itemSvc = new ItemApplicationService(itemRepo, catSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.file.application.NoOpPresignedUrlGenerator(), new com.sseulang.domain.item.application.NoOpWishlistView());
        ChatRoomApplicationService roomSvc = new ChatRoomApplicationService(roomRepo, itemSvc, org.mockito.Mockito.mock(com.sseulang.domain.user.application.UserApplicationService.class), new com.sseulang.domain.chat.application.NoOpUserView(), new com.sseulang.domain.chat.application.NoOpItemView());
        NotificationApplicationService notifSvc = new NotificationApplicationService(notifRepo);
        // 단위 테스트에선 트랜잭션 컨텍스트 X — AFTER_COMMIT listener 직접 호출하는 fake event publisher.
        ChatRealtimeEventListener listener = new ChatRealtimeEventListener(publisher);
        org.springframework.context.ApplicationEventPublisher eventPublisher = event -> {
            if (event instanceof ChatRealtimePublishRequestedEvent e) {
                listener.onPublish(e);
            }
        };
        service = new MessageApplicationService(msgRepo, roomSvc, notifSvc, publisher, eventPublisher);

        Item item = itemRepo.save(Item.create(SELLER, null, "물건", "d", 1L, null, null, TradeType.판매, null));
        roomId = roomSvc.openFor(BUYER, item.getId()).id();
    }

    @Test
    @DisplayName("send 텍스트_정상_ChatRoom 메타 + Notification + broadcast")
    void send_text_정상() {
        MessageResult r = service.send(new MessageSendCommand(roomId, BUYER, "안녕", null));

        assertThat(r.content()).isEqualTo("안녕");
        assertThat(r.senderId()).isEqualTo(BUYER);

        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        assertThat(room.getLastMessage()).isEqualTo("안녕");
        assertThat(room.getUser1Unread()).isEqualTo(1);  // SELLER unread
        assertThat(room.getUser2Unread()).isZero();      // BUYER 본인

        // 상대방(SELLER) 에게만 Notification 생성 + push
        assertThat(publisher.notificationPublications).hasSize(1);
        assertThat(publisher.notificationPublications.get(0).userId()).isEqualTo(SELLER);

        // 채팅방 토픽 broadcast
        assertThat(publisher.chatRoomPublications).hasSize(1);
        assertThat(publisher.chatRoomPublications.get(0).roomId()).isEqualTo(roomId);
    }

    @Test
    @DisplayName("send 이미지_정상_preview=[사진]")
    void send_image_정상() {
        MessageResult r = service.send(new MessageSendCommand(
                roomId, BUYER, null, List.of("https://img/1", "https://img/2")));

        assertThat(r.imageUrls()).hasSize(2);

        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        assertThat(room.getLastMessage()).isEqualTo("[사진 2장]");

        assertThat(publisher.notificationPublications).hasSize(1);
    }

    @Test
    @DisplayName("send 외부인_CHAT_FORBIDDEN_broadcast 없음")
    void send_외부인_거부() {
        assertThatThrownBy(() -> service.send(new MessageSendCommand(roomId, OUTSIDER, "x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);

        assertThat(publisher.chatRoomPublications).isEmpty();
        assertThat(publisher.notificationPublications).isEmpty();
    }

    @Test
    @DisplayName("send 없는 방_CHAT_ROOM_NOT_FOUND")
    void send_없는_방() {
        assertThatThrownBy(() -> service.send(new MessageSendCommand(9999L, BUYER, "x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("listPage 최신순 + 커서 페이징")
    void listPage_커서() {
        for (int i = 0; i < 5; i++) {
            service.send(new MessageSendCommand(roomId, BUYER, "msg-" + i, null));
        }

        List<MessageResult> first = service.listPage(roomId, BUYER, null, 3);
        assertThat(first).hasSize(3);
        assertThat(first.get(0).content()).isEqualTo("msg-4");

        List<MessageResult> next = service.listPage(roomId, BUYER, first.get(2).id(), 10);
        assertThat(next).hasSize(2);
        assertThat(next.get(0).content()).isEqualTo("msg-1");
    }

    @Test
    @DisplayName("listPage 외부인_CHAT_FORBIDDEN")
    void listPage_외부인_거부() {
        assertThatThrownBy(() -> service.listPage(roomId, OUTSIDER, null, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_FORBIDDEN);
    }

    @Test
    @DisplayName("seller 가 보낸 메시지_buyer 에게 Notification + buyer unread+1")
    void send_seller_buyer_unread() {
        service.send(new MessageSendCommand(roomId, SELLER, "응", null));

        ChatRoom room = roomRepo.findById(roomId).orElseThrow();
        assertThat(room.getUser1Unread()).isZero();      // SELLER 본인
        assertThat(room.getUser2Unread()).isEqualTo(1);  // BUYER unread

        assertThat(publisher.notificationPublications).hasSize(1);
        assertThat(publisher.notificationPublications.get(0).userId()).isEqualTo(BUYER);
    }
}
