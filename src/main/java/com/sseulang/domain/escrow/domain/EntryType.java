package com.sseulang.domain.escrow.domain;

/**
 * 거래대행 신청 진입 경로.
 *
 * <ul>
 *   <li>{@link #EXTERNAL} — 기존 link 토큰 흐름. initiator 가 link 생성, receiver 가 form 제출.
 *       link_id 가 application 에 채워짐.</li>
 *   <li>{@link #INTERNAL} — 채팅방 내 신청 (PR-B-2 라운드 12). 판매자가 채팅방 안에서 신청 + 양쪽 정보 한 번에 입력.
 *       link_id 는 NULL, chat_room_id 가 채워짐.</li>
 * </ul>
 */
public enum EntryType {
    INTERNAL,
    EXTERNAL
}
