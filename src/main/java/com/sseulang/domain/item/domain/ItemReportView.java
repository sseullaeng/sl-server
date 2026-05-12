package com.sseulang.domain.item.domain;

import java.util.Collection;
import java.util.Map;

// 라운드 12 — admin item 화면용 신고 카운트 view 포트. 어댑터는 report/infrastructure 에 위치.
public interface ItemReportView {

    Map<Long, Long> countByItemIds(Collection<Long> itemIds);
}
