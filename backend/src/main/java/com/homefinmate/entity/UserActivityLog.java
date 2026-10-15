package com.homefinmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 사용자 기능 이용 기록 (user_activity_logs 테이블)
@Entity
// 최신순 조회용 인덱스 (관리자 화면)
@Table(name = "user_activity_logs", indexes = @Index(name = "idx_activity_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사용자 id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 기능 이름
    @Column(nullable = false)
    private String action;

    @Column(name = "trace_id")
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
