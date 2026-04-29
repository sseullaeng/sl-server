package com.sseulang.domain.report.infrastructure.persistence;

import com.sseulang.domain.report.domain.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserReportJpaRepository extends JpaRepository<UserReport, Long> {
}
