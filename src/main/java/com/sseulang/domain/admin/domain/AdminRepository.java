package com.sseulang.domain.admin.domain;

import java.util.Optional;

public interface AdminRepository {

    Admin save(Admin admin);

    Optional<Admin> findByUsername(String username);

    Optional<Admin> findById(Long id);
}
