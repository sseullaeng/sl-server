package com.sseulang.domain.report.infrastructure.persistence;

import com.sseulang.domain.report.domain.UserReport;
import com.sseulang.domain.report.domain.UserReportRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserReportRepositoryImpl implements UserReportRepository {

    private final UserReportJpaRepository jpa;

    public UserReportRepositoryImpl(UserReportJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public UserReport save(UserReport report) {
        return jpa.save(report);
    }
}
