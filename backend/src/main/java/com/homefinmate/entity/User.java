package com.homefinmate.entity;

import com.homefinmate.security.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 회원 엔티티 (users 테이블)
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디 (중복 불가)
    @Column(nullable = false, unique = true)
    private String email;

    // BCrypt 해시된 비밀번호
    @Column(nullable = false)
    private String password;

    // 역할 (DB에는 문자열로 저장)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // AES 암호화된 소득 (선택 입력)
    @Column(name = "encrypted_income")
    private String encryptedIncome;

    // 가입 시각 (자동 기록, 수정 불가)
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
