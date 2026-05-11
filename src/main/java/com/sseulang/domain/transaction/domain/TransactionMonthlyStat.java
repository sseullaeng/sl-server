package com.sseulang.domain.transaction.domain;

import java.time.YearMonth;

public record TransactionMonthlyStat(YearMonth month, long count, long amount) { }
