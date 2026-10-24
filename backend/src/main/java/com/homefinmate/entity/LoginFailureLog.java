package com.homefinmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 로그인 실패 기록
@Entity
// 최신순 조회용 인덱스 (관리자 화면)
@Table(name = "login_failure_logs", indexes = {
        @Index(name = "idx_login_failure_created", columnList = "created_at"),
        // 로그인 시도 제한용 (V4)
        @Index(name = "idx_login_failure_email_created", columnList = "email, created_at") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginFailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 시도한 이메일
    @Column(nullable = false)
    private String email;

    // 실패한 이유(서버 내부 기록용)
    @Column
    private String reason;

    @Column(name = "trace_id")
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
