package com.homefinmate.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Flyway 마이그레이션 검증: 기존 DB(이력 없음)는 V2만, 빈 DB는 V1, V2 실행
class FlywayMigrationTest {

    private String newDbUrl() {
        return "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private Flyway flyway(String url, String target) {
        var config = Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(true).baselineVersion("1");
        return (target == null ? config : config.target(target)).load();
    }

    private List<String> loanSimulationColumns(Statement st) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("select lower(column_name) from information_schema.columns"
                + " where lower(table_name) = 'loan_simulations'")) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        return columns;
    }

    // 운영 DB 상황: 테이블과 dsr 기록은 있고 Flyway 이력은 없음 → V1은 건너뛰고 V2로 dsr 값을 dti로 옮김
    @Test
    @DisplayName("[TC-DATA-007] Flyway 이력이 없는 기존 DB는 V1을 건너뛰고 V2 실행: dsr 값을 dti로 옮기고 기준 NEW_LOAN_ONLY, dsr 컬럼 삭제")
    void existingDbWithoutHistory_migratesDsrToDti() throws Exception {
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            // ddl-auto: update로 만든 DB 재현: V1 테이블만 만들고 Flyway 이력은 지움
            flyway(url, "1").migrate();
            st.execute("drop table \"flyway_schema_history\"");
            st.execute("insert into loan_simulations (user_id, loan_amount, interest_rate, dsr)"
                    + " values (7, 300000000, 4.5, 0.3800), (7, 100000000, 4.5, null)");

            // V2만 확인하려고 버전 2까지만 실행 (V3는 TC-DATA-009)
            MigrateResult result = flyway(url, "2").migrate();

            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(loanSimulationColumns(st)).contains("dti", "dti_basis").doesNotContain("dsr");
            try (ResultSet rs = st.executeQuery("select dti, dti_basis from loan_simulations order by id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal("dti")).isEqualByComparingTo("0.3800");
                assertThat(rs.getString("dti_basis")).isEqualTo("NEW_LOAN_ONLY");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal("dti")).isNull();
                assertThat(rs.getString("dti_basis")).isEqualTo("NEW_LOAN_ONLY");
            }
        }
    }

    // 새로 띄운 DB: V1(테이블 생성) → V2(DTI 전환) 순서로 실행
    @Test
    @DisplayName("[TC-DATA-008] 빈 DB는 V1·V2·V3를 차례로 실행해 dti·dti_basis 컬럼이 있고 dsr 컬럼은 없음")
    void emptyDb_runsAllMigrations() throws Exception {
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            MigrateResult result = flyway(url, null).migrate();

            assertThat(result.migrationsExecuted).isEqualTo(3);
            assertThat(loanSimulationColumns(st)).contains("dti", "dti_basis").doesNotContain("dsr");
        }
    }

    // V2까지 적용된 DB(기록 있음)에 V3 실행: 기존 값 유지, 넓어진 범위의 값 저장 가능
    @Test
    @DisplayName("[TC-DATA-009] V3는 기존 값을 유지하고, 연이율 10% 이상·DTI 10 이상을 저장할 수 있게 함 (V3 전에는 실패)")
    void v3_widensRateAndDti_keepingExistingValues() throws Exception {
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            flyway(url, "2").migrate();
            st.execute("insert into loan_simulations (user_id, loan_amount, interest_rate, dti, dti_basis)"
                    + " values (7, 300000000, 4.5000, 0.3800, 'NEW_LOAN_ONLY')");
            String wide = "insert into loan_simulations (user_id, loan_amount, interest_rate, dti, dti_basis)"
                    + " values (7, 300000000, 10.5000, 15.2006, 'WITH_EXISTING_INTEREST')";
            assertThatThrownBy(() -> st.execute(wide)).isInstanceOf(SQLException.class);

            MigrateResult result = flyway(url, null).migrate();

            assertThat(result.migrationsExecuted).isEqualTo(1);
            st.execute(wide);
            try (ResultSet rs = st.executeQuery("select interest_rate, dti from loan_simulations order by id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal("interest_rate")).isEqualByComparingTo("4.5");
                assertThat(rs.getBigDecimal("dti")).isEqualByComparingTo("0.38");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal("interest_rate")).isEqualByComparingTo("10.5");
                assertThat(rs.getBigDecimal("dti")).isEqualByComparingTo("15.2006");
            }
        }
    }
}
