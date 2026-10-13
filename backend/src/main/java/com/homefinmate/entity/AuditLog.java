package com.homefinmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 감사 기록 (audit_logs 테이블, 누가/무엇을/언제)
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 행위자 종류 (USER, SYSTEM)
    @Column(name = "actor_type", nullable = false)
    private String actorType;

    // 행위자 식별값 (USER면 이메일)
    @Column(name = "actor_id")
    private String actorId;

    // 행동 이름
    @Column(nullable = false)
    private String action;

    @Column(name = "trace_id")
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
