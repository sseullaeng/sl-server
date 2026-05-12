package com.sseulang.domain.report.infrastructure;

import com.sseulang.domain.item.domain.ItemReportView;
import com.sseulang.domain.report.domain.UserReportRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class ItemReportViewAdapter implements ItemReportView {

    private final UserReportRepository repository;

    public ItemReportViewAdapter(UserReportRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, Long> countByItemIds(Collection<Long> itemIds) {
        return repository.countByItemIds(itemIds);
    }
}
