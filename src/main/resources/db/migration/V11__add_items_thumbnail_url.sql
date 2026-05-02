-- =============================================================================
-- V11 — items.thumbnail_url 컬럼 추가
-- 목록·검색·찜·내물품 응답(ItemSummaryResponse)에서 카드 UI 썸네일 1장을 N+1 없이
-- 내려주기 위한 denormalize. sortOrder=1 + is_thumbnail=true 의 image_url 과 동기화.
-- 동기화 책임: Item Aggregate Root (addImage / clearImages 메서드).
-- =============================================================================
ALTER TABLE items
    ADD COLUMN thumbnail_url VARCHAR(500) NULL AFTER region;

-- 기존 행 backfill — 각 item 의 썸네일(is_thumbnail=true) 이미지의 url 로 업데이트.
-- 다중 row 가 동시에 thumbnail=true 일 일은 없지만 안전하게 ANY_VALUE 사용.
UPDATE items i
   SET i.thumbnail_url = (
        SELECT img.image_url
          FROM item_images img
         WHERE img.item_id = i.id AND img.is_thumbnail = TRUE
         ORDER BY img.sort_order ASC
         LIMIT 1
   );
