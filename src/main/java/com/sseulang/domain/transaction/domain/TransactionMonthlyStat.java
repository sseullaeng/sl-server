package com.sseulang.domain.transaction.domain;

import java.time.YearMonth;

/**
 * 월별 거래 집계 결과 — Admin dashboard 차트(recharts)용.
 *
 * @param month YYYY-MM 단위 (해당 월에 거래완료된 거래)
 * @param count 거래완료 건수
 * @param amount 합계 금액 (KRW)
 */
public record TransactionMonthlyStat(YearMonth month, long count, long amount) { }
