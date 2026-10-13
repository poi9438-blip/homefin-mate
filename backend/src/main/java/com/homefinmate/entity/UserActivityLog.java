package com.homefinmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 사용자 기능 이용 기록 (user_activity_logs 테이블)
@Entity
@Table(name = "user_activity_logs")
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
