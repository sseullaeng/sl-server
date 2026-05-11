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

public class InMemoryFakeUserReportRepository implements UserReportRepository {

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

    @Override
    public Map<Long, Long> countByTargetUserIds(java.util.Collection<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Set<Long> idSet = new java.util.HashSet<>(targetUserIds);
        Map<Long, Long> result = new HashMap<>();
        for (UserReport r : store.values()) {
            if (idSet.contains(r.getReportedId())) {
                result.merge(r.getReportedId(), 1L, Long::sum);
            }
        }
        return result;
    }

    @Override
    public long countPending() {
        return store.values().stream()
                .filter(r -> r.getStatus() == ReportStatus.접수 || r.getStatus() == ReportStatus.처리중)
                .count();
    }

    @Override
    public long countResolved() {
        return store.values().stream()
                .filter(r -> r.getStatus() == ReportStatus.처리완료 || r.getStatus() == ReportStatus.반려)
                .count();
    }

    @Override
    public long countCreatedSince(java.time.LocalDateTime since) {
        return store.values().stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(since))
                .count();
    }
}
