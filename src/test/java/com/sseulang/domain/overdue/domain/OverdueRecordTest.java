package com.sseulang.domain.overdue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OverdueRecordTest {

    private static final LocalDateTime RENTAL_END = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final LocalDateTime STARTED = RENTAL_END.plusHours(24);
    private static final OverdueThresholds THRESHOLDS = new OverdueThresholds(50_000, 14, 7, 20);

    private static OverdueRecord record(long depositAmount) {
        return OverdueRecord.create(
                100L,
                11L,
                20L,
                depositAmount,
                RENTAL_END,
                STARTED
        );
    }

    @Test
    @DisplayName("create_초기상태는_진행중_Phase1_0일차")
    void create_initial_state() {
        OverdueRecord record = record(100_000L);

        assertThat(record.getStatus()).isEqualTo(OverdueStatus.진행중);
        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_1);
        assertThat(record.getLegalAction()).isEqualTo(OverdueLegalAction.NONE);
        assertThat(record.getOverdueDays()).isZero();
        assertThat(record.getDepositForfeitedAmount()).isZero();
        assertThat(record.getExtraDebtAmount()).isZero();
        assertThat(record.remainingDepositAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("advanceDay_1일차_보증금_30퍼센트_몰수")
    void advanceDay_day_1() {
        OverdueRecord record = record(100_000L);

        record.advanceDay(STARTED, THRESHOLDS);

        assertThat(record.getOverdueDays()).isEqualTo(1);
        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_1);
        assertThat(record.getDepositForfeitedAmount()).isEqualTo(30_000L);
        assertThat(record.remainingDepositAmount()).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("advanceDay_같은날_재실행은_멱등")
    void advanceDay_idempotent_same_day() {
        OverdueRecord record = record(100_000L);

        record.advanceDay(STARTED.plusDays(2), THRESHOLDS);
        record.advanceDay(STARTED.plusDays(2), THRESHOLDS);

        assertThat(record.getOverdueDays()).isEqualTo(3);
        assertThat(record.getDepositForfeitedAmount()).isEqualTo(50_000L);
        assertThat(record.getExtraDebtAmount()).isZero();
    }

    @Test
    @DisplayName("advanceDay_8일차_Phase2_추가채무_발생")
    void advanceDay_phase_2() {
        OverdueRecord record = record(100_000L);

        record.advanceDay(STARTED.plusDays(7), THRESHOLDS);

        assertThat(record.getOverdueDays()).isEqualTo(8);
        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_2);
        assertThat(record.getDepositForfeitedAmount()).isEqualTo(90_000L);
        assertThat(record.getExtraDebtAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("shouldSuspendAccount_금액_또는_일수_임계값_도달")
    void shouldSuspendAccount_threshold() {
        OverdueRecord byAmount = record(100_000L);
        byAmount.advanceDay(STARTED, THRESHOLDS);

        OverdueRecord byDays = record(1_000L);
        byDays.advanceDay(STARTED.plusDays(13), THRESHOLDS);

        assertThat(byAmount.shouldSuspendAccount(new OverdueThresholds(30_000, 14, 7, 20))).isTrue();
        assertThat(byDays.shouldSuspendAccount(THRESHOLDS)).isTrue();
    }

    @Test
    @DisplayName("markAccountSuspended_Phase3_전이")
    void markAccountSuspended() {
        OverdueRecord record = record(100_000L);
        LocalDateTime suspendedAt = STARTED.plusDays(14);

        record.markAccountSuspended(suspendedAt);

        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_3);
        assertThat(record.getAccountSuspendedAt()).isEqualTo(suspendedAt);
        assertThat(record.shouldEnterLegalAction(THRESHOLDS, suspendedAt.plusDays(7))).isTrue();
    }

    @Test
    @DisplayName("markLegalAction_Phase4_및_법적조치중_전이")
    void markLegalAction() {
        OverdueRecord record = record(100_000L);

        record.markLegalAction(OverdueLegalAction.내용증명);

        assertThat(record.getLegalAction()).isEqualTo(OverdueLegalAction.내용증명);
        assertThat(record.getPhase()).isEqualTo(OverduePhase.PHASE_4);
        assertThat(record.getStatus()).isEqualTo(OverdueStatus.법적조치중);
    }

    @Test
    @DisplayName("markResolved_채무_0이면_종료")
    void markResolved_without_debt() {
        OverdueRecord record = record(100_000L);
        record.advanceDay(STARTED, THRESHOLDS);

        record.markResolved(STARTED.plusDays(1), "반납 완료");

        assertThat(record.getStatus()).isEqualTo(OverdueStatus.종료);
        assertThat(record.getResolvedAt()).isEqualTo(STARTED.plusDays(1));
        assertThat(record.getResolutionNote()).isEqualTo("반납 완료");
    }

    @Test
    @DisplayName("markResolved_추가채무_있으면_정산완료")
    void markResolved_with_debt() {
        OverdueRecord record = record(100_000L);
        record.advanceDay(STARTED.plusDays(7), THRESHOLDS);

        record.markResolved(STARTED.plusDays(8), "반납 완료");

        assertThat(record.getStatus()).isEqualTo(OverdueStatus.정산완료);
        assertThat(record.getExtraDebtAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("create_invalid_입력_거부")
    void create_invalid() {
        assertThatThrownBy(() -> OverdueRecord.create(null, 11L, 20L, 100L, RENTAL_END, STARTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OverdueRecord.create(100L, 11L, 11L, 100L, RENTAL_END, STARTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OverdueRecord.create(100L, 11L, 20L, -1L, RENTAL_END, STARTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OverdueRecord.create(100L, 11L, 20L, 100L, RENTAL_END, RENTAL_END.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
