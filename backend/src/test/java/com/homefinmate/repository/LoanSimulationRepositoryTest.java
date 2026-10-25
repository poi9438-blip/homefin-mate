package com.homefinmate.repository;

import com.homefinmate.support.PersonalDataJpaConfig;
import org.springframework.context.annotation.Import;
import com.homefinmate.entity.LoanSimulation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// LoanSimulationRepository 조회 순서와 interest_rate 컬럼 범위 검증
@DataJpaTest
@Import(PersonalDataJpaConfig.class)
@ActiveProfiles("test")
class LoanSimulationRepositoryTest {
        @Autowired
        private LoanSimulationRepository loanSimulationRepository;

        private LoanSimulation newSimulation(Long userId, String loanAmount, String interestRate) {
                return LoanSimulation.builder()
                                .userId(userId)
                                .loanAmount(new BigDecimal(loanAmount))
                                .interestRate(new BigDecimal(interestRate))
                                .termMonths(360)
                                .build();
        }

        // 다른 사용자 것은 안 섞임
        @Test
        @DisplayName("[TC-DATA-005] 사용자별 이력은 최신순, findTop은 가장 최근 1건")
        void history_isNewestFirst_andTopIsLatest() throws InterruptedException {
                loanSimulationRepository.save(newSimulation(1L, "100000000", "4.5"));
                Thread.sleep(10);
                loanSimulationRepository.save(newSimulation(1L, "200000000", "4.5"));
                Thread.sleep(10);
                loanSimulationRepository.save(newSimulation(2L, "999000000", "4.5"));

                List<LoanSimulation> history = loanSimulationRepository.findByUserIdOrderByCreatedAtDesc(1L);

                assertThat(history).extracting(s -> s.getLoanAmount().intValue())
                                .containsExactly(200_000_000, 100_000_000);
                assertThat(loanSimulationRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                                .isPresent()
                                .get()
                                .extracting(s -> s.getLoanAmount().intValue())
                                .isEqualTo(200_000_000);
                assertThat(loanSimulationRepository.findTopByUserIdOrderByCreatedAtDesc(99L)).isEmpty();
        }

        // 사용자별 계산 수와 최근 5건(최신순)
        @Test
        @DisplayName("[TC-DATA-006] 사용자별 계산 수(COUNT)와 최근 5건(최신순)")
        void countAndTop5_perUser() throws InterruptedException {
                for (int i = 1; i <= 7; i++) {
                        loanSimulationRepository.save(newSimulation(1L, String.valueOf(i * 1_000_000), "4.5"));
                        Thread.sleep(5);
                }
                loanSimulationRepository.save(newSimulation(2L, "999000000", "4.5"));

                assertThat(loanSimulationRepository.countByUserId(1L)).isEqualTo(7);
                assertThat(loanSimulationRepository.findTop5ByUserIdOrderByCreatedAtDesc(1L))
                                .extracting(sim -> sim.getLoanAmount().intValue())
                                .containsExactly(7_000_000, 6_000_000, 5_000_000, 4_000_000, 3_000_000);
                assertThat(loanSimulationRepository.countByUserId(99L)).isZero();
        }

        @Autowired
        private jakarta.persistence.EntityManager entityManager;

        // DB 컬럼에는 암호문(숫자 아님), 엔티티로 읽으면 원래 DTI (#31)
        @Test
        @DisplayName("[TC-DATA-011] DTI는 DB에 암호문으로 저장되고, 엔티티로 읽으면 원래 값 (#31)")
        void dti_isEncryptedInDb_andDecryptedOnRead() {
                LoanSimulation saved = loanSimulationRepository.save(LoanSimulation.builder()
                                .userId(7L).loanAmount(new BigDecimal("300000000")).interestRate(new BigDecimal("4.5"))
                                .termMonths(360).dti(new BigDecimal("0.3800")).build());
                entityManager.flush();
                entityManager.clear();

                String raw = (String) entityManager
                                .createNativeQuery("select dti_encrypted from loan_simulations where id = :id")
                                .setParameter("id", saved.getId()).getSingleResult();
                assertThat(raw).isNotBlank().doesNotContain("0.38");
                assertThat(loanSimulationRepository.findById(saved.getId()).orElseThrow().getDti())
                                .isEqualByComparingTo("0.3800");
        }
}
