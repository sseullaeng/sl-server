package com.sseulang.domain.admin.infrastructure.persistence;

import com.sseulang.domain.admin.domain.Admin;
import com.sseulang.domain.admin.domain.AdminRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdminRepositoryImpl implements AdminRepository {

    private final AdminJpaRepository jpa;

    public AdminRepositoryImpl(AdminJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Admin save(Admin admin) {
        return jpa.save(admin);
    }

    @Override
    public Optional<Admin> findByUsername(String username) {
        return jpa.findByUsername(username);
    }

    @Override
    public Optional<Admin> findById(Long id) {
        return jpa.findById(id);
    }
}
