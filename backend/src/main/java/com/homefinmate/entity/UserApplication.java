package com.homefinmate.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 사용자의 FinFlow 신청 번호 (user_applications 테이블, V8)
@Entity
@Table(name = "user_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // FinFlow 신청 번호 (UUID)
    @Column(name = "application_id", nullable = false, updatable = false)
    private String applicationId;

    @Column(name = "product_code", nullable = false, updatable = false)
    private String productCode;

    @Column(name = "simulation_id")
    private Long simulationId;

    @Column(name = "trace_id", updatable = false)
    private String traceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
