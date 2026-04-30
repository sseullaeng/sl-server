package com.sseulang.domain.report.application;

import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.domain.UserReport;
import com.sseulang.domain.report.domain.UserReportRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryFakeUserReportRepository implements UserReportRepository {

    private final Map<Long, UserReport> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public UserReport save(UserReport report) {
        if (report.getId() == null) {
            ReflectionTestUtils.setField(report, "id", ++sequence);
        }
        store.put(report.getId(), report);
        return report;
    }

    @Override
    public Optional<UserReport> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<UserReport> findByStatus(ReportStatus status, Pageable pageable) {
        List<UserReport> filtered = store.values().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .sorted(Comparator.comparing(UserReport::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }
}
