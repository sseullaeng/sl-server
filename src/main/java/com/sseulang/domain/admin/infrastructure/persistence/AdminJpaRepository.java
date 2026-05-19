package com.sseulang.domain.admin.infrastructure.persistence;

import com.sseulang.domain.admin.domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AdminJpaRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);
}
