package com.homefinmate.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 대출 계산 결과 (loan_simulations 테이블)
@Entity
// 사용자별 최신순 조회용 인덱스
@Table(name = "loan_simulations", indexes = @Index(name = "idx_loan_sim_user_created", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 계산한 사용자 id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 대출 원금 (원, 소수 2자리)
    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    // 연이율 (%)
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal interestRate;

    // 월 상환액 (원리금균등)
    @Column(name = "monthly_payment", precision = 15, scale = 2)
    private BigDecimal monthlyPayment;

    // 총이자 (월 상환액 * 기간 - 원금)
    @Column(name = "total_interest", precision = 15, scale = 2)
    private BigDecimal totalInterest;

    // DSR (월 상환액 / 월 소득)
    @Column(name = "dsr", precision = 5, scale = 4)
    private BigDecimal dsr;

    // 대출 기간 (개월)
    @Column(name = "term_months")
    private Integer termMonths;

    // 지역 (선택 입력)
    @Column(name = "region")
    private String region;

    @Column(name = "trace_id")
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
