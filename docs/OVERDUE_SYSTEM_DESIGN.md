# 쓸랭 대여 연체 시스템 설계안

라운드 14 escrow 대여 lifecycle 위에 얹는 후속 도메인입니다. Phase 1~4 정책 자동화와 관리자 운영 화면을 목표로 합니다.

## 1. 도메인 위치 / 구조

신규 도메인:

```text
com.sseulang.domain.overdue/
├── application/
│   ├── OverdueApplicationService
│   ├── OverdueDetectionScheduler
│   └── dto/
│       └── OverdueChargeQueryResult
├── domain/
│   ├── OverdueRecord
│   ├── OverdueRecordRepository
│   ├── OverduePhase
│   ├── OverdueStatus
│   ├── LateFeeCalculator
│   ├── OverdueLegalAction
│   └── event/
│       ├── OverdueStartedEvent
│       ├── OverduePhaseChangedEvent
│       └── OverdueAccountSuspendedEvent
├── infrastructure/
│   └── persistence/OverdueRecordJpaRepository
└── presentation/
    ├── AdminOverdueController
    └── dto/AdminOverdueResponse, OverdueLegalActionRequest
```

`overdue` 도메인은 `escrow`, `point`, `user` 도메인의 `ApplicationService`만 호출합니다. 다른 도메인의 Repository를 직접 참조하지 않습니다.

## 2. 도메인 모델

### 2.1 OverdueRecord

`OverdueRecord`는 1개 `EscrowApplication`에 1개만 존재하는 Aggregate Root입니다.

```java
class OverdueRecord {
    Long id;
    Long escrowApplicationId;
    Long buyerId;
    Long sellerId;
    Long depositAmount;
    LocalDateTime rentalEndAt;
    LocalDateTime overdueStartedAt;
    int overdueDays;
    OverduePhase phase;
    OverdueStatus status;
    long depositForfeitedAmount;
    long extraDebtAmount;
    LocalDateTime accountSuspendedAt;
    OverdueLegalAction legalAction;
    LocalDateTime resolvedAt;
    String resolutionNote;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    void advanceDay(LocalDateTime now, OverdueThresholds thresholds);

    void markResolved(LocalDateTime now, String note);

    void markLegalAction(OverdueLegalAction action);
}
```

도메인 메서드만 외부에 노출하고 setter는 열지 않습니다.

### 2.2 OverduePhase

| 값 | 의미 |
|---|---|
| `PHASE_1` | 보증금 차감, 1~7일 |
| `PHASE_2` | 보증금 초과 채무 누적, 8일차부터 |
| `PHASE_3` | 계정 정지 발동 |
| `PHASE_4` | 법적 조치 단계 |

### 2.3 OverdueStatus

| 값 | 의미 |
|---|---|
| `진행중` | 활성 상태. 매일 `advanceDay` 대상 |
| `정산완료` | 반납 완료. 잔여 보증금 환불, 채무는 남을 수 있음 |
| `법적조치중` | Phase 4 진입, 운영팀 처리 대기 |
| `종료` | 모든 채무 해소 |

### 2.4 OverdueLegalAction

| 값 | 의미 |
|---|---|
| `NONE` | 법적 조치 없음 |
| `내용증명` | 내용증명 단계 |
| `분쟁조정` | 분쟁조정 단계 |
| `소송제기` | 소송 제기 단계 |

### 2.5 정책값

```java
@ConfigurationProperties("app.overdue")
record OverdueThresholds(
    int phase3AmountKrw,
    int phase3DaysThreshold,
    int phase4DaysAfterSuspend,
    int phase2DailyRatePercent
) {
}
```

기본값:

| 설정 | 기본값 | 의미 |
|---|---:|---|
| `phase3AmountKrw` | `50_000` | Phase 3 계정 정지 금액 기준 |
| `phase3DaysThreshold` | `14` | Phase 3 계정 정지 일수 기준 |
| `phase4DaysAfterSuspend` | `7` | 정지 후 Phase 4 진입까지의 일수 |
| `phase2DailyRatePercent` | `20` | Phase 2 일별 추가 채무율 |

`application.yml`에 가시화합니다. 운영 중 변경은 재배포로 처리합니다.

## 3. 마이그레이션

### 3.1 V39__overdue_records.sql

```sql
CREATE TABLE overdue_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    escrow_application_id BIGINT NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    deposit_amount BIGINT NOT NULL,
    rental_end_at DATETIME NOT NULL,
    overdue_started_at DATETIME NOT NULL,
    overdue_days INT NOT NULL DEFAULT 0,
    phase VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deposit_forfeited_amount BIGINT NOT NULL DEFAULT 0,
    extra_debt_amount BIGINT NOT NULL DEFAULT 0,
    account_suspended_at DATETIME NULL,
    legal_action VARCHAR(30) NOT NULL DEFAULT 'NONE',
    resolved_at DATETIME NULL,
    resolution_note VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_overdue_buyer (buyer_id, status),
    INDEX idx_overdue_status_phase (status, phase),
    INDEX idx_overdue_active (status, overdue_days),
    CONSTRAINT fk_overdue_escrow FOREIGN KEY (escrow_application_id)
        REFERENCES escrow_applications(id) ON DELETE RESTRICT,
    CONSTRAINT fk_overdue_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT fk_overdue_seller FOREIGN KEY (seller_id) REFERENCES users(id)
);
```

### 3.2 V40__user_overdue_debt.sql

구매자의 누적 채무 표시와 다음 결제 시 우선 차감 hook에서 사용합니다.

```sql
ALTER TABLE users
    ADD COLUMN overdue_debt_balance BIGINT NOT NULL DEFAULT 0
    COMMENT '연체로 인한 누적 채무 (Phase 2). 다음 충전/결제 시 우선 차감';

CREATE INDEX idx_users_overdue_debt ON users (overdue_debt_balance);
```

### 3.3 V41__overdue_audit_columns.sql

```sql
ALTER TABLE point_histories
    ADD COLUMN overdue_record_id BIGINT NULL
    COMMENT '연체 정산 관련 history 인 경우 OverdueRecord ID';
```

`PointReferenceType` enum에 `OVERDUE` 값을 추가합니다.

## 4. 핵심 알고리즘

`LateFeeCalculator`는 Spring 의존성이 없는 순수 도메인 로직입니다.

```java
record DailyForfeit(int day, int rate) {
}

class LateFeeCalculator {
    private static final List<DailyForfeit> PHASE1_TABLE = List.of(
        new DailyForfeit(1, 30),
        new DailyForfeit(2, 10),
        new DailyForfeit(3, 10),
        new DailyForfeit(4, 10),
        new DailyForfeit(5, 10),
        new DailyForfeit(6, 10),
        new DailyForfeit(7, 10)
    );

    static LateFeeResult calculate(long deposit, int overdueDays, int phase2RatePercent) {
        if (overdueDays <= 0) {
            return LateFeeResult.zero(deposit);
        }

        int forfeitPercent = 0;
        for (int i = 0; i < Math.min(overdueDays, 7); i++) {
            forfeitPercent += PHASE1_TABLE.get(i).rate();
        }

        long forfeited = deposit * forfeitPercent / 100;
        long remainingDeposit = deposit - forfeited;

        long extraDebt = 0;
        if (overdueDays > 7) {
            int phase2Days = overdueDays - 7;
            extraDebt = deposit * phase2RatePercent / 100 * phase2Days;
        }

        return new LateFeeResult(forfeited, remainingDeposit, extraDebt);
    }
}

record LateFeeResult(long forfeited, long remainingDeposit, long extraDebt) {
    static LateFeeResult zero(long deposit) {
        return new LateFeeResult(0, deposit, 0);
    }
}
```

돈 산정 로직이므로 단위 테스트는 필수입니다. `0`, `1`, `7`, `8`, `30`일 boundary와 큰 값 overflow를 검증합니다.

## 5. 스케줄러

`OverdueDetectionScheduler`는 매일 새벽 2시 KST에 실행합니다. `AutoWithdrawalScheduler`의 1시 실행과 시간을 분리합니다.

```java
@Component
class OverdueDetectionScheduler {
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<EscrowApplication> targets = escrowService.findOverdueCandidates(now.minusHours(24));
        for (var escrow : targets) {
            try {
                overdueService.startOverdue(escrow.getId(), now);
            } catch (Exception ex) {
                log.error("failed to start overdue. escrowApplicationId={}", escrow.getId(), ex);
            }
        }

        List<Long> activeIds = overdueService.findActiveRecordIds(BATCH_LIMIT);
        for (Long id : activeIds) {
            try {
                overdueService.advanceDay(id, now);
            } catch (Exception ex) {
                log.error("failed to advance overdue day. overdueRecordId={}", id, ex);
            }
        }
    }
}
```

### 5.1 startOverdue

```java
@Transactional
void startOverdue(Long escrowAppId, LocalDateTime now) {
    EscrowApplication escrow = escrowAppService.getForOverdue(escrowAppId);
    if (overdueRepo.existsByEscrowApplicationId(escrowAppId)) {
        return;
    }

    OverdueRecord record = OverdueRecord.create(
        escrowAppId,
        escrow.getBuyerId(),
        escrow.getSellerId(),
        escrow.getDepositAmount(),
        escrow.getRentalEndAt(),
        now
    );
    overdueRepo.save(record);

    advanceDay(record.getId(), now);

    notificationService.send(
        record.getBuyerId(),
        "[연체] 반납 기한 초과 — 1일차 보증금 30% 차감"
    );
}
```

### 5.2 advanceDay

```java
@Transactional
void advanceDay(Long recordId, LocalDateTime now) {
    OverdueRecord record = overdueRepo.findByIdForUpdate(recordId).orElseThrow();
    if (record.status() != OverdueStatus.진행중) {
        return;
    }

    int prevDays = record.overdueDays();
    int newDays = (int) Duration.between(record.overdueStartedAt(), now).toDays() + 1;
    if (newDays <= prevDays) {
        return;
    }

    LateFeeResult prevFee = LateFeeCalculator.calculate(
        record.depositAmount(),
        prevDays,
        threshold.phase2DailyRatePercent()
    );
    LateFeeResult newFee = LateFeeCalculator.calculate(
        record.depositAmount(),
        newDays,
        threshold.phase2DailyRatePercent()
    );

    long deltaForfeit = newFee.forfeited() - prevFee.forfeited();
    long deltaDebt = newFee.extraDebt() - prevFee.extraDebt();

    if (deltaForfeit > 0) {
        userService.releaseHold(record.buyerId(), deltaForfeit);
        pointService.credit(
            record.sellerId(),
            deltaForfeit,
            PointHistoryType.연체몰수,
            PointReferenceType.OVERDUE,
            recordId,
            "연체 보증금 몰수 — " + newDays + "일차"
        );
    }

    if (deltaDebt > 0) {
        userService.incrementOverdueDebt(record.buyerId(), deltaDebt);
    }

    record.advanceDay(newDays, newFee.forfeited(), newFee.extraDebt(), now);

    OverduePhase nextPhase = decidePhase(record, threshold);
    if (nextPhase != record.phase()) {
        record.setPhase(nextPhase);
        eventPublisher.publish(new OverduePhaseChangedEvent(record.id(), nextPhase));
    }

    if (shouldSuspendAccount(record, threshold) && record.accountSuspendedAt() == null) {
        userService.adminAutoSuspend(record.buyerId(), "연체 임계값 도달");
        record.markAccountSuspended(now);
        notificationService.sendToAdmin("연체 임계값 도달 — userId=" + record.buyerId());
    }

    if (shouldEnterLegalAction(record, threshold, now) && record.phase() != OverduePhase.PHASE_4) {
        record.setPhase(OverduePhase.PHASE_4);
        notificationService.sendToAdmin("연체 법적 조치 단계 진입 — recordId=" + record.id());
    }
}
```

### 5.3 Phase 결정

```java
OverduePhase decidePhase(OverdueRecord record, OverdueThresholds threshold) {
    if (record.legalAction() != OverdueLegalAction.NONE) {
        return OverduePhase.PHASE_4;
    }
    if (record.accountSuspendedAt() != null) {
        return OverduePhase.PHASE_3;
    }
    if (record.overdueDays() > 7) {
        return OverduePhase.PHASE_2;
    }
    return OverduePhase.PHASE_1;
}

boolean shouldSuspendAccount(OverdueRecord record, OverdueThresholds threshold) {
    long totalDebt = record.depositForfeitedAmount() + record.extraDebtAmount();
    return totalDebt >= threshold.phase3AmountKrw()
        || record.overdueDays() >= threshold.phase3DaysThreshold();
}

boolean shouldEnterLegalAction(OverdueRecord record, OverdueThresholds threshold, LocalDateTime now) {
    if (record.accountSuspendedAt() == null) {
        return false;
    }
    return Duration.between(record.accountSuspendedAt(), now).toDays()
        >= threshold.phase4DaysAfterSuspend();
}
```

## 6. 정산 종료

### 6.1 반납 완료

`EscrowApplicationService.confirmReturn` 끝에 hook을 추가합니다.

```java
overdueService.markResolvedByReturn(escrowApp.id(), now);
```

`markResolvedByReturn` 처리:

| 처리 | 정책 |
|---|---|
| 잔여 hold | Phase 1에서 살아남은 보증금은 구매자에게 환불 |
| `extraDebtAmount` | `users.overdue_debt_balance`에 유지. 반납으로 자동 소멸하지 않음 |
| record status | 기본 `정산완료`, 채무가 0이면 `종료` |

### 6.2 채무 입금

구매자가 토스로 충전하면 `PaymentApplicationService.handleChargeCompleted` 끝에서 채무를 우선 차감합니다.

```java
long debt = userService.findOverdueDebt(buyerId);
if (debt > 0) {
    long deduct = Math.min(charged, debt);
    userService.decrementOverdueDebt(buyerId, deduct);
    overdueService.recordDebtPayment(buyerId, deduct);
}
```

## 7. 기존 도메인 통합 지점

| 위치 | 변경 |
|---|---|
| `EscrowApplication.markRentalEnd` | 변경 없음. 기존 자동 반납 로직 유지 |
| `EscrowApplicationService.confirmReturn` | 마지막에 `overdueService.markResolvedByReturn` 호출 |
| `EscrowApplicationService` 신규 query | `findOverdueCandidates(LocalDateTime cutoff)` |
| `UserApplicationService` 신규 메서드 | `incrementOverdueDebt`, `decrementOverdueDebt`, `findOverdueDebt`, `adminAutoSuspend(reason)` |
| `PaymentApplicationService.handleChargeCompleted` | 마지막에 채무 우선 차감 hook |
| `User` 엔티티 | `overdueDebtBalance` 필드와 도메인 메서드 |
| `PointHistoryType` enum | `연체몰수`, `연체채무상환` 추가 |
| `PointReferenceType` enum | `OVERDUE` 추가 |
| `AdminUserResponse` | `overdueDebt`, `activeOverdueRecordId` 필드 추가 |

## 8. API 엔드포인트

### 8.1 관리자

```http
GET /api/v1/admin/overdue?status=진행중&phase=PHASE_2&page=&size=
GET /api/v1/admin/overdue/{id}
PATCH /api/v1/admin/overdue/{id}/legal-action
PATCH /api/v1/admin/overdue/{id}/resolve
POST /api/v1/admin/overdue/{id}/recompute
```

`PATCH /api/v1/admin/overdue/{id}/legal-action` 요청:

```json
{
  "action": "내용증명"
}
```

허용 값은 `내용증명`, `분쟁조정`, `소송제기`입니다. 처리 시 `legalAction`을 변경하고 `PHASE_4`로 강제 전이합니다.

`PATCH /api/v1/admin/overdue/{id}/resolve` 요청:

```json
{
  "note": "관리자 처리 메모"
}
```

외부 합의 등 운영상 강제 종료가 필요한 경우 사용합니다.

### 8.2 사용자

```http
GET /api/v1/users/me/overdue
GET /api/v1/users/me/overdue-debt
```

## 9. 알림

모든 알림은 `NotificationApplicationService`를 통해 생성하고 MongoDB에 저장합니다.

| 트리거 | 수신자 | 내용 |
|---|---|---|
| Day 1 감지 | buyer | `[연체] 반납 1일 초과 — 보증금 30% 차감 시작` |
| Phase 2 진입, Day 8 | buyer | `[연체] 보증금 초과 채무 누적 시작` |
| Phase 3 정지 | buyer + admin | `[정지] 연체로 인한 계정 정지` |
| Phase 4 진입 | admin | `[법적조치] recordId=X 검토 필요` |
| 반납 완료 | buyer | `[정산완료] 잔여 보증금 X원 환불` |
| 채무 입금 | buyer | `[채무상환] X원 차감 — 잔여 Y원` |

## 10. 동시성 / 멱등성

| 항목 | 정책 |
|---|---|
| 중복 생성 | `overdue_records.escrow_application_id` UNIQUE |
| `advanceDay` 재실행 | `prevDays >= newDays`이면 no-op |
| record race | `overdue_records` 행에 `PESSIMISTIC_WRITE` 락 |
| 채무 잔액 | `UPDATE users SET overdue_debt_balance = overdue_debt_balance + ? WHERE id = ?` 원자 갱신 |
| 스케줄러 중복 실행 | 현재 단일 인스턴스 가정. 다중 인스턴스 전환 시 ShedLock 도입 |

## 11. 테스트 전략

보증금 차감과 포인트 정산 영역이므로 TDD 강제 영역으로 봅니다.

### 11.1 Domain 단위 테스트

| 테스트 | 검증 |
|---|---|
| `LateFeeCalculatorTest` | 0/1/7/8/14/30일, boundary, 큰 값 overflow |
| `OverdueRecordTest` | `advanceDay` 멱등, phase 전이, `markResolved` |
| `UserTest` | `overdueDebtBalance` 증가/감소 |

### 11.2 Application 단위 테스트

| 테스트 | 검증 |
|---|---|
| `OverdueApplicationServiceTest` | `startOverdue` 신규/중복 가드 |
| `OverdueApplicationServiceTest` | `advanceDay` phase 전이와 Phase 3 자동 정지 |
| `OverdueApplicationServiceTest` | `markResolvedByReturn` 잔여 hold 환불 |
| `OverdueApplicationServiceTest` | 채무 감소 음수 방지 |

### 11.3 통합 테스트

| 테스트 | 검증 |
|---|---|
| `OverdueLifecycleIT` | handover -> rentalEndAt 경과 -> scheduler -> 7일 advance -> Phase 2 -> Phase 3 -> 반납 -> 정산 |
| `PaymentDebtHookIT` | buyer 충전 시 채무 우선 차감 |

### 11.4 동시성 테스트

| 테스트 | 검증 |
|---|---|
| `OverdueDebtConcurrencyTest` | 두 스케줄러 동시 실행 시 채무 중복 누적 방지 |

## 12. 약관 / 운영 준비물

코드 외 산출물:

- `docs/POLICY_OVERDUE.md`: 연체 정책 정식 문서. FE 표시와 운영팀 SOP에 사용
- 회원가입 약관: 연체 시 보증금 몰수, 추가 채무, 계정 정지, 법적조치 문구 추가
- 관리자 화면 매뉴얼: Phase 4 진입 시 처리 절차, 내용증명 양식 등

## 13. 작업 분할

| PR | 내용 | 의존 |
|---|---|---|
| PR1 | 도메인 + 마이그레이션 + `LateFeeCalculator` + 단위테스트 | 없음 |
| PR2 | `OverdueApplicationService` + Repository + escrow hook `markResolvedByReturn` | PR1 |
| PR3 | `OverdueDetectionScheduler` + 알림 + Phase 3 자동 정지 | PR2 |
| PR4 | Payment 채무 차감 hook + `AdminOverdueController` | PR3 |
| PR5 | 사용자 본인 조회 endpoint + `AdminUserResponse` 필드 추가 | PR4 |
| PR6 | 통합 테스트 IT + 약관/문서 | PR1~5 |
관
예상 작업량은 집중 기준 1.5~2일입니다.

## 14. 미정 영역

구현 진입 전 정책 결정을 완료해야 합니다.

| 항목 | 권장안 | 대안 |
|---|---|---|
| 연체 시작 기준 | `rentalEndAt + 24h` | `rentalEndAt` 즉시 |
| Phase 1 몰수금 분배 | seller 100% | seller 70% + platform 20% |
| Phase 2 채무 회수 | 채무 기록 + 다음 결제 우선 차감 | 외부 추심 강제 |
| 반납 시 채무 처리 | 채무 유지 | 자동 소멸 |
| Phase 3 제재 | 채무 해소까지 시한부 정지 | 영구 차단 |
| 임계값 | 본 문서 기본값 사용 | PM 협의 후 조정 |
