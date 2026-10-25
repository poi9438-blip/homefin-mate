package com.homefinmate.repository;

import com.homefinmate.migration.V5__ProtectPersonalData;
import com.homefinmate.util.EmailHasher;
import com.homefinmate.util.EncryptionUtil;
import org.springframework.test.util.ReflectionTestUtils;
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

// Flyway 마이그레이션 검증: 기존 DB(이력 없음)는 V2만, 빈 DB는 V1부터 끝까지 실행
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
    @DisplayName("[TC-DATA-008] 빈 DB는 V1~V6을 차례로 실행해 dti_encrypted·dti_basis 컬럼이 있고 dsr 컬럼은 없음")
    void emptyDb_runsAllMigrations() throws Exception {
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            MigrateResult result = Flyway.configure().dataSource(url, "sa", "")
                    .javaMigrations(v5()).load().migrate();

            assertThat(result.migrationsExecuted).isEqualTo(6);
            assertThat(loanSimulationColumns(st)).contains("dti_encrypted", "dti_basis").doesNotContain("dsr", "dti");
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

            MigrateResult result = flyway(url, "3").migrate();

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

    // 평문이 있는 DB에 V5 실행: DTI는 암호문(복호화하면 원래 값), 실패 기록 이메일은 HMAC, 감사 행위자는 user:{id}
    @Test
    @DisplayName("[TC-DATA-012] V5: 기존 DTI는 암호문으로, 로그인 실패 이메일은 HMAC으로, 감사 기록 이메일은 user:{id}·user:deleted로 바뀜 (#31, #33)")
    void v5_protectsExistingPersonalData() throws Exception {
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "encryptionKeyConfig", "HomeFin-AES-256-Key-Test-Padded!");
        EmailHasher emailHasher = new EmailHasher("HomeFin-AES-256-Key-Test-Padded!");
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            flyway(url, "4").migrate();
            st.execute("insert into users (id, email, password, role) values (42, 'kim@test.com', 'x', 'USER')");
            st.execute("insert into loan_simulations (user_id, loan_amount, interest_rate, dti, dti_basis)"
                    + " values (42, 300000000, 4.5, 0.3800, 'WITH_EXISTING_INTEREST')");
            st.execute("insert into login_failure_logs (email, reason) values ('Kim@Test.com', 'Incorrect password')");
            st.execute("insert into audit_logs (actor_type, actor_id, action) values"
                    + " ('USER', 'kim@test.com', 'LOGIN_SUCCESS'), ('USER', 'gone@test.com', 'LOGIN_SUCCESS'),"
                    + " ('SYSTEM', 'HOMEFIT_MATE', 'RISKWATCH_INTEGRATION_FAILURE: x')");

            // V5만 실행 (V6은 TC-DATA-015·016)
            MigrateResult result = Flyway.configure().dataSource(url, "sa", "").target("5")
                    .javaMigrations(new V5__ProtectPersonalData(encryptionUtil, emailHasher)).load().migrate();

            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(loanSimulationColumns(st)).contains("dti_encrypted").doesNotContain("dti");
            try (ResultSet rs = st.executeQuery("select dti_encrypted from loan_simulations")) {
                assertThat(rs.next()).isTrue();
                String encrypted = rs.getString(1);
                assertThat(encrypted).doesNotContain("0.38");
                assertThat(encryptionUtil.decrypt(encrypted)).isEqualTo("0.3800");
            }
            try (ResultSet rs = st.executeQuery("select email_hash from login_failure_logs")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(emailHasher.hash("kim@test.com"));
            }
            try (ResultSet rs = st.executeQuery("select actor_id from audit_logs order by id")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("user:42");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("user:deleted");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("HOMEFIT_MATE");
            }
        }
    }

    // 테스트 키로 만든 V5 (V5까지 실행해야 V6을 실행할 수 있음)
    private V5__ProtectPersonalData v5() {
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        ReflectionTestUtils.setField(encryptionUtil, "encryptionKeyConfig", "HomeFin-AES-256-Key-Test-Padded!");
        return new V5__ProtectPersonalData(encryptionUtil, new EmailHasher("HomeFin-AES-256-Key-Test-Padded!"));
    }

    private Flyway flywayWithV5(String url, String target) {
        return Flyway.configure().dataSource(url, "sa", "").target(target).javaMigrations(v5()).load();
    }

    private List<String> activityActions(Statement st) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("select user_id, action, trace_id from user_activity_logs order by id")) {
            while (rs.next()) {
                rows.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3));
            }
        }
        return rows;
    }

    // V6: 통합 분석 때 같이 남은 LOAN_CALCULATION만 삭제
    @Test
    @DisplayName("[TC-DATA-015] V6: 같은 사용자·traceId로 통합 분석 기록이 있는 LOAN_CALCULATION만 삭제 (대출 계산만 한 기록·traceId 없는 기록은 남김, #57)")
    void v6_removesDuplicatedLoanCalculationActivity() throws Exception {
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            flywayWithV5(url, "5").migrate();
            st.execute("insert into users (id, email, password, role) values"
                    + " (1, 'hmac:a', 'x', 'USER'), (2, 'hmac:b', 'x', 'USER')");
            st.execute("insert into user_activity_logs (user_id, action, trace_id) values"
                    + " (1, 'LOAN_CALCULATION', 't-analyze'), (1, 'UNIFIED_ANALYSIS', 't-analyze'),"
                    + " (1, 'LOAN_CALCULATION', 't-loan-only'),"
                    + " (1, 'LOAN_CALCULATION', null), (1, 'UNIFIED_ANALYSIS', null),"
                    + " (2, 'LOAN_CALCULATION', 't-analyze')");

            MigrateResult result = flywayWithV5(url, "6").migrate();

            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThat(activityActions(st)).containsExactly(
                    "1:UNIFIED_ANALYSIS:t-analyze",
                    "1:LOAN_CALCULATION:t-loan-only",
                    "1:LOAN_CALCULATION:null", "1:UNIFIED_ANALYSIS:null",
                    "2:LOAN_CALCULATION:t-analyze");
        }
    }

    // V6 외래키: 없는 사용자 id 저장 거부, 사용자 삭제 시 계산 기록·이용 기록 함께 삭제
    @Test
    @DisplayName("[TC-DATA-016] V6 외래키: 없는 사용자 id로 계산·이용 기록 저장 거부, 사용자를 지우면 그 사용자 기록만 함께 삭제 (#32)")
    void v6_foreignKeys_rejectUnknownUser_andCascadeDelete() throws Exception {
        String url = newDbUrl();
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
                Statement st = conn.createStatement()) {
            flywayWithV5(url, "6").migrate();
            st.execute("insert into users (id, email, password, role) values"
                    + " (1, 'hmac:a', 'x', 'USER'), (2, 'hmac:b', 'x', 'USER')");
            st.execute("insert into loan_simulations (user_id, loan_amount, interest_rate) values"
                    + " (1, 300000000, 4.5), (2, 100000000, 4.5)");
            st.execute("insert into user_activity_logs (user_id, action) values"
                    + " (1, 'LOAN_CALCULATION'), (2, 'LOAN_CALCULATION')");

            assertThatThrownBy(() -> st.execute("insert into loan_simulations (user_id, loan_amount, interest_rate)"
                    + " values (99, 300000000, 4.5)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.execute("insert into user_activity_logs (user_id, action)"
                    + " values (99, 'LOAN_CALCULATION')")).isInstanceOf(SQLException.class);

            st.execute("delete from users where id = 1");

            try (ResultSet rs = st.executeQuery("select user_id from loan_simulations")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(2L);
                assertThat(rs.next()).isFalse();
            }
            try (ResultSet rs = st.executeQuery("select user_id from user_activity_logs")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(2L);
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
