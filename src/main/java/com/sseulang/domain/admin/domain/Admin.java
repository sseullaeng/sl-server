package com.sseulang.domain.admin.domain;

import com.sseulang.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static Admin create(String username, String hashedPassword, String name) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username 은 필수입니다");
        }
        if (hashedPassword == null || hashedPassword.isBlank()) {
            throw new IllegalArgumentException("hashedPassword 는 필수입니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 은 필수입니다");
        }
        Admin admin = new Admin();
        admin.username = username;
        admin.password = hashedPassword;
        admin.name = name;
        admin.role = "ADMIN";
        admin.active = true;
        return admin;
    }
}
