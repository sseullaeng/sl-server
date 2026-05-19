-- V17: admin_id FK 일괄 drop (OAuth admin 흐름 호환)
--
-- 배경: app.admin.user-emails 화이트리스트 매치 OAuth 사용자 (users 테이블) 가 ROLE_ADMIN 으로
-- admin chain 통과 가능. 그러나 banners / notices / user_reports / withdrawals /
-- escrow_fee_settings 의 admin_id (또는 updated_by) 컬럼이 admins(id) FK 라
-- OAuth admin (subject=user.id, admins.id 와 다름) 으로 INSERT 시 FK constraint fail (1452).
--
-- 결정: admin_id 컬럼 자체는 audit 용으로 유지하되 FK 가드만 drop. 보안 영향 미미 —
-- INSERT 자체가 admin chain 에서만 가능 (ROLE_ADMIN 강제). admin_id 가 임의 값으로
-- 들어가도 admin 권한이 있다는 사실 자체는 보장됨.
--
-- 대안 (R1): admins 와 users 통합 또는 OAuth admin 자동 admins.id 매핑. 본 PR 은 운영
-- 진입 직전 빠른 우회.

ALTER TABLE banners              DROP FOREIGN KEY fk_banners_admin;
ALTER TABLE notices              DROP FOREIGN KEY fk_notices_admin;
ALTER TABLE user_reports         DROP FOREIGN KEY fk_user_reports_admin;
ALTER TABLE withdrawals          DROP FOREIGN KEY fk_withdrawals_admin;
ALTER TABLE escrow_fee_settings  DROP FOREIGN KEY fk_escrow_fee_settings_admin;
