package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.ChatRoomCard;
import com.sseulang.domain.chat.domain.ChatRoomCardRepository;
import java.util.Optional;

/**
 * MongoDB 없는 슬라이스 테스트 (예: SettlementRollbackIT 등 @DataJpaTest)에서 @Import 로 명시 등록.
 * 항상 빈 결과 — 시스템 카드 fetch 가 빠짐.
 *
 * <p>@Component 제거 — SpringBootTest 자동 스캔 시 ChatRoomCardRepositoryImpl 과 충돌 방지.
 * @Import 가 클래스 import 시 Spring 이 빈 등록 (no-arg 생성자 활용).</p>
 */
public class NoOpChatRoomCardRepository implements ChatRoomCardRepository {

    @Override
    public Optional<ChatRoomCard> findByChatRoomId(Long chatRoomId) {
        return Optional.empty();
    }

    @Override
    public ChatRoomCard save(ChatRoomCard card) {
        return card;
    }
}
