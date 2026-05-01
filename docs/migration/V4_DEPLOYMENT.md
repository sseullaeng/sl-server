# V4 prod 배포 절차 — withdrawals UNIQUE index

> follow-up #32. V4 (`V4__add_withdrawal_idempotency_key.sql`) 는 `withdrawals` 에 컬럼 1개 추가 + UNIQUE 인덱스 1개 생성. prod 배포 시 metadata lock 으로 짧은 쓰기 차단이 발생할 수 있어 절차를 명시.

## 변경 내용

```sql
ALTER TABLE withdrawals
    ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER user_id,
    ADD UNIQUE KEY uk_withdrawals_user_idem (user_id, idempotency_key);
```

- 컬럼 추가: NULL 허용 → 기존 row 재기록 X (instant DDL).
- UNIQUE 인덱스: 모든 row 의 `(user_id, idempotency_key)` 조합 검증 필요 → **table copy + metadata lock**.

## 배포 시 영향

| 데이터 규모 | 예상 시간 (MySQL 8 기준) | 락 영향 |
|---|---|---|
| ~1만 row | 1초 미만 | 무시 가능 |
| ~10만 row | 수 초 | 그 시간 동안 INSERT/UPDATE 대기 |
| ~100만 row 이상 | 수십 초 ~ 수 분 | 결제/출금 흐름 영향 큼 |

> 쓸랭 배포 시점(5/6) 기준 withdrawals 는 0~수십 row → **즉시 적용 안전**. 이 문서는 향후 재차 UNIQUE 인덱스 추가 시 참조용.

## 절차 — 데이터 적은 경우 (기본)

```bash
# 1. 백업
mysqldump --single-transaction sseulang withdrawals > /tmp/withdrawals_backup_$(date +%s).sql

# 2. Flyway 자동 실행 — 백엔드 서버 시작 시
docker compose up -d backend

# 3. 검증
mysql -u sseulang -p sseulang -e "SHOW INDEX FROM withdrawals WHERE Key_name='uk_withdrawals_user_idem';"
mysql -u sseulang -p sseulang -e "SELECT * FROM flyway_schema_history WHERE version='4';"
```

## 절차 — 데이터 많은 경우 (수십만 row 이상)

Flyway 가 metadata lock 잡고 길게 멈추면 결제/출금 트래픽이 막힌다. **수동으로 pt-online-schema-change 사용 후 Flyway 는 baseline-on-migrate 로 스킵.**

```bash
# 1. 백업
mysqldump --single-transaction sseulang withdrawals > /tmp/withdrawals_backup_$(date +%s).sql

# 2. pt-online-schema-change — 락 없이 새 테이블 만들고 trigger 로 동기화
pt-online-schema-change \
  --alter "ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER user_id, \
           ADD UNIQUE KEY uk_withdrawals_user_idem (user_id, idempotency_key)" \
  --execute D=sseulang,t=withdrawals

# 3. Flyway 에 V4 적용됐다고 알림 (실행은 스킵)
mysql -u sseulang -p sseulang -e "
  INSERT INTO flyway_schema_history (installed_rank, version, description, type,
    script, checksum, installed_by, installed_on, execution_time, success)
  VALUES (4, '4', 'add withdrawal idempotency key', 'SQL',
    'V4__add_withdrawal_idempotency_key.sql', NULL, 'sseulang', NOW(), 0, 1);
"

# 4. 백엔드 재시작 — Flyway 가 V4 이미 적용된 것으로 간주하고 V5 부터 적용
docker compose up -d backend
```

> ⚠️ pt-online-schema-change 의 checksum 은 NULL — 향후 V4 파일 내용이 바뀌면 Flyway 가 checksum mismatch 에러. 현재 파일은 freeze 상태로 유지.

## 롤백

UNIQUE 인덱스가 만들어진 후 중복 데이터가 들어올 일이 없으므로 롤백 거의 불필요. 만약 컬럼 자체를 제거해야 하면:

```sql
ALTER TABLE withdrawals
    DROP INDEX uk_withdrawals_user_idem,
    DROP COLUMN idempotency_key;
```

후 `flyway_schema_history` 에서 V4 row 삭제. 백엔드 코드는 V4 컬럼을 사용하므로 함께 이전 버전으로 배포 필요.

## 검증 체크리스트

- [ ] `SHOW INDEX FROM withdrawals` 에 `uk_withdrawals_user_idem` 존재
- [ ] `SHOW CREATE TABLE withdrawals` 에 `idempotency_key VARCHAR(64) NULL` 컬럼 존재
- [ ] `flyway_schema_history` 의 V4 `success=1`
- [ ] 동일 `(user_id, idempotency_key)` 로 출금 두 번 시도 → 두 번째 거부 (E2E 검증)
- [ ] 백엔드 로그에 Flyway migration 에러 없음

## 향후 UNIQUE 인덱스 추가 시 동일 절차 적용 영역

| 후보 | 영향 |
|---|---|
| `chat_rooms (user1_id, user2_id, item_id)` | 중복 채팅방 방지 (현재는 코드 레벨 체크) |
| `reviews (transaction_id, reviewer_id)` | 한 거래당 본인 리뷰 1건 (현재 V1 에 이미 적용됨) |
| `payments (merchant_uid)` | 결제 멱등성 (V1 에 이미 적용됨) |

새 UNIQUE 인덱스 마이그레이션 작성 시 본 문서를 참조해 데이터 규모를 먼저 확인할 것.
