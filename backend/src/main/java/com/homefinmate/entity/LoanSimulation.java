package com.homefinmate.entity;

import com.homefinmate.util.EncryptedDecimalConverter;
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

    // 계산한 사용자 id (users.id 외래키, 사용자 삭제 시 함께 삭제, V6)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 대출 원금 (원, 소수 2자리)
    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    // 연이율 (%)
    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal interestRate;

    // 월 상환액 (원리금균등)
    @Column(name = "monthly_payment", precision = 15, scale = 2)
    private BigDecimal monthlyPayment;

    // 총이자 (월 상환액 * 기간 - 원금)
    @Column(name = "total_interest", precision = 15, scale = 2)
    private BigDecimal totalInterest;

    // DTI ((월 상환액 + 기존 대출 월 이자) / 월 소득)
    // DTI는 암호문으로 저장 (소득 역산 방지 #31, 읽을 때 자동 복호화)
    @Convert(converter = EncryptedDecimalConverter.class)
    @Column(name = "dti_encrypted")
    private BigDecimal dti;

    // DTI 계산 기준 (DtiRule.NEW_LOAN_ONLY / WITH_EXISTING_INTEREST)
    @Column(name = "dti_basis", length = 30)
    private String dtiBasis;

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
