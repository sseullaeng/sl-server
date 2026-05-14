# 쓸랭 대여 연체 정책 (운영 SOP + 사용자 고지)

> 거래대행 대여 (`escrow.rentalMode=true`) 의 반납 기한 (`rentalEndAt`) 초과 시 자동 적용되는 연체 처리 정책. FE 표시 + 운영팀 수동 처리 절차 통합.
>
> 마지막 갱신: 2026-05-14. 코드: `src/main/java/com/sseulang/domain/overdue/`.

---

## 1. 연체 발동 조건

| 조건 | 값 |
|---|---|
| 대상 | `escrow.tradeMode=INTERNAL` + `escrow.rentalMode=true` + `escrow.status IN (사용중, 반납중)` |
| 발동 시점 | `rentalEndAt + 24h ≤ now` (스케줄러 매일 02:00 KST) |
| 1 escrow ↔ 1 record | `overdue_records.escrow_application_id` UNIQUE |

스케줄러: `OverdueDetectionScheduler.run()` — cron `0 0 2 * * *` Asia/Seoul.

---

## 2. 차감 / 채무 산정 (LateFeeCalculator)

### 2.1 Phase 1 — 보증금 차감 (1~7일)

| 연체일 | 누적 차감률 | 잔여 보증금 |
|---|---|---|
| 1 | 30% | 70% |
| 2 | 40% | 60% |
| 3 | 50% | 50% |
| 4 | 60% | 40% |
| 5 | 70% | 30% |
| 6 | 80% | 20% |
| 7 | 90% | 10% |

차감된 보증금 = **판매자 보상**. `pointBalance += deltaForfeit`, `PointHistoryType.연체몰수`.

### 2.2 Phase 2 — 보증금 초과 채무 (8일차~)

```
일별 추가 채무 = 보증금 × phase2DailyRatePercent (default 20%)
```

예: 보증금 100,000원 + 8일차 → 90,000 (몰수 누적) + 20,000 (Day 8 추가) = **총 채무 110,000**.

추가 채무는 `users.overdue_debt_balance` 에 누적 — 다음 충전 시 우선 차감 (§3.2).

### 2.3 정책값 (`application.yml`)

```yaml
app:
  overdue:
    phase3-amount-krw: 50000        # 계정 정지 금액 임계
    phase3-days-threshold: 14       # 계정 정지 일수 임계
    phase4-days-after-suspend: 7    # 정지 후 법적 조치 검토까지 대기일
    phase2-daily-rate-percent: 20   # Phase 2 일별 채무율
```

운영 중 변경은 재배포로.

---

## 3. Phase 3 / 4 — 자동 제재 + 수동 검토

### 3.1 Phase 3 — 계정 자동 정지

조건 (둘 중 하나):
- `depositForfeitedAmount + extraDebtAmount ≥ phase3AmountKrw` (default 50,000원)
- `overdueDays ≥ phase3DaysThreshold` (default 14일)

동작:
- `userApplicationService.adminAutoSuspend(buyerId, 30, "연체 임계값 도달")` — 30일 정지
- Refresh Token 즉시 revoke
- `record.markAccountSuspended(now)` + Phase 3 전이
- buyer 알림 발송 ("연체로 인한 계정 정지")
- `cumulativeSuspendDays` 누적 → 200일 도달 시 `AutoWithdrawalScheduler` 가 자동 탈퇴

### 3.2 Phase 4 — 법적 조치 (관리자 수동)

조건: `accountSuspendedAt + phase4DaysAfterSuspend ≤ now`.

자동 동작: `log.warn("[overdue] Phase 4 — 법적 조치 검토 필요 ...")` 발송 (admin 알림 시스템 도입 후 자동 알림으로 대체 예정).

운영팀 수동 절차:
1. 관리자 화면에서 PHASE_4 후보 목록 확인 (`GET /api/v1/admin/overdue?phase=PHASE_3` + `accountSuspendedAt + 7일` 경과 필터)
2. 사례 검토 (대상자 history, 채무 규모, 반납 가능성)
3. 단계별 조치:
   - 1단계: **내용증명** 발송 (법무팀 외부 발송 후 endpoint 호출)
   - 2단계: **분쟁조정** 신청
   - 3단계: **소송 / 형사고소**
4. 각 단계마다 endpoint 호출:
   ```
   PATCH /api/v1/admin/overdue/{id}/legal-action
   Body: { "action": "내용증명" }    # or "분쟁조정", "소송제기"
   ```
   → `record.markLegalAction(action)` → `status=법적조치중`, `phase=PHASE_4`
5. 처리 종료 시:
   ```
   PATCH /api/v1/admin/overdue/{id}/resolve
   Body: { "note": "외부 합의 종결" }
   ```

---

## 4. 정산 종료 흐름

### 4.1 정상 반납 (escrow.confirmReturn)

`EscrowApplicationService.confirmReturn` 끝에서 `overdueService.markResolvedByReturn(escrowApplicationId)` 자동 호출.

| 항목 | 처리 |
|---|---|
| 잔여 보증금 (Phase 1 잔여분) | buyer 환불 (`refundHold`) |
| 누적 채무 (`extraDebtAmount`) | **삭감 X** — `users.overdue_debt_balance` 에 유지 |
| record status | 채무 0 → `종료`, 채무 > 0 → `정산완료` |

### 4.2 채무 입금 (charge hook)

buyer 가 토스 충전 시 `PaymentApplicationService.applyPaymentConfirmedEffects` 끝에서:

```
debt = users.overdue_debt_balance
if debt > 0:
    deduct = min(charged, debt)
    pointBalance -= deduct      (PointHistoryType.연체채무상환)
    overdue_debt_balance -= deduct
    notification: "[채무상환] X원 차감, 잔여 Y원"
    if 채무 잔여 0:
        모든 정산완료 record → 종료
```

### 4.3 관리자 강제 종료

`PATCH /admin/overdue/{id}/resolve` — 외부 합의 등 운영상 종결.

진행중 record 강제 종료 시 잔여 보증금 buyer 환불 + status 종료/정산완료.

---

## 5. 알림 매트릭스

| 시점 | 수신자 | 제목 | 내용 |
|---|---|---|---|
| Day 1 신규 감지 | buyer | 연체 반납 기한 초과 | 1일차 보증금 30% 차감 |
| Phase 1 → 2 (Day 8) | buyer | 보증금 초과 채무 누적 | 일 20% 추가 채무 적용 |
| Phase 3 자동 정지 | buyer | 연체로 인한 계정 정지 | 30일 정지, 채무 해소 후 검토 |
| Phase 4 진입 | admin | (log.warn) | 법적 조치 검토 필요 |
| 반납 완료 | buyer | 정산완료 | 잔여 보증금 환불 + 잔여 채무 안내 |
| 채무 입금 | buyer | 채무상환 | X원 차감, 잔여 Y원 |
| 법적 조치 단계 전이 | buyer | 법적 조치 통보 | 내용증명/분쟁조정/소송제기 단계 명시 |

---

## 6. API 엔드포인트 요약

### 관리자
| Method | Path | 용도 |
|---|---|---|
| GET | `/api/v1/admin/overdue?status=&phase=&page=` | 목록 |
| GET | `/api/v1/admin/overdue/{id}` | 단건 |
| PATCH | `/api/v1/admin/overdue/{id}/legal-action` | 법적 조치 단계 전이 |
| PATCH | `/api/v1/admin/overdue/{id}/resolve` | 강제 종료 |
| POST | `/api/v1/admin/overdue/{id}/recompute` | 디버그 재계산 |

### 본인
| Method | Path | 용도 |
|---|---|---|
| GET | `/api/v1/users/me/overdue` | 진행중/정산완료/법적조치중 record |
| GET | `/api/v1/users/me/overdue-debt` | 누적 채무 잔액 |

### AdminUserResponse 신규 필드
- `overdueDebt` — User.overdueDebtBalance
- `activeOverdueRecordId` — MVP 에선 null

---

## 7. 약관 / 사용자 고지 문구 (FE 표시용 권장)

대여 거래대행 신청 단계 (FE 약관 동의 또는 결제 직전 모달):

> **연체 시 처리 안내**
>
> 반납 기한 (`rentalEndAt`) 경과 24시간 후부터 연체로 처리되며, 다음 정책이 자동 적용됩니다.
>
> 1. **1~7일차**: 보증금 일별 차감 (1일 30% / 2~7일 일 10%, 누적 90%까지). 차감된 보증금은 판매자에게 보상으로 지급됩니다.
> 2. **8일차부터**: 보증금 외 추가 채무가 매일 누적됩니다 (보증금의 20%/일).
> 3. **누적 채무 5만원 이상 또는 14일 경과** 시 계정이 30일간 자동 정지됩니다.
> 4. 정지 후 7일 경과 시 **내용증명 / 분쟁조정 / 민사·형사 절차** 가 검토될 수 있습니다.
> 5. 누적된 추가 채무는 다음 결제/충전 시 우선 차감됩니다.
> 6. 신용평가기관 연동 시 신용등급 하락 가능성이 있으며, 쓸랭이 판매자에게 손해 보상 후 **구상권 행사** 를 통해 회수할 수 있습니다.

---

## 8. 운영 체크리스트

| 주기 | 작업 | 위치 |
|---|---|---|
| 매일 | 스케줄러 정상 실행 로그 확인 | `[overdue-scheduler]` 키워드로 prod log grep |
| 매일 | Phase 4 후보 (정지 + 7일 경과) 검토 | `GET /admin/overdue?phase=PHASE_3` |
| 주간 | 신규 PHASE_4 진입 건 처리 | `GET /admin/overdue?phase=PHASE_4` |
| 분기 | 임계값 (`app.overdue.*`) 정책 검토 | `application-prod.yml` |

---

## 9. 코드 참조

| 파일 | 역할 |
|---|---|
| `domain/overdue/domain/OverdueRecord.java` | Aggregate Root + 상태 전이 |
| `domain/overdue/domain/LateFeeCalculator.java` | 차감 / 채무 계산 (순수 도메인) |
| `domain/overdue/application/OverdueApplicationService.java` | 트랜잭션 경계 |
| `domain/overdue/application/OverdueDetectionScheduler.java` | 매일 02:00 cron |
| `domain/overdue/presentation/AdminOverdueController.java` | 관리자 endpoint |
| `domain/overdue/presentation/MyOverdueController.java` | 본인 endpoint |
| `domain/escrow/application/EscrowApplicationService.java#confirmReturn` | 반납 정산 hook |
| `domain/payment/application/PaymentApplicationService.java#applyOverdueDebtDeduction` | 충전 시 채무 차감 hook |

---

## 10. 미완 (후속)

| 항목 | 비고 |
|---|---|
| Admin notification system | Phase 4 진입 시 admin 자동 알림 (현재 log.warn) |
| 카카오 콘솔 외 자동 추심 | 외부 추심사 연동 시 endpoint 추가 |
| 신용평가기관 연동 | 사업자 인증 + 데이터 공유 약정 후 |
| ProviderId migration tool | 카카오/구글 앱 변경 시 social_id 일괄 갱신 (overdue 와 별개) |
