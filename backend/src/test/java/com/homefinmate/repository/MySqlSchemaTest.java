package com.homefinmate.repository;

import com.homefinmate.entity.User;
import com.homefinmate.migration.V5__ProtectPersonalData;
import com.homefinmate.security.Role;
import com.homefinmate.util.EmailHasher;
import com.homefinmate.util.EncryptionUtil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 실제 MySQL 8.0에서 마이그레이션·스키마 검증·MySQL 전용 SQL 확인 (Testcontainers)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlSchemaTest {

        @Container
        static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

        // 데이터소스를 컨테이너 MySQL로, Hibernate는 엔티티와 실제 테이블이 맞는지 검사
        @DynamicPropertySource
        static void mysqlProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
                registry.add("spring.datasource.username", MYSQL::getUsername);
                registry.add("spring.datasource.password", MYSQL::getPassword);
                registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
                registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }

        @Autowired
        JdbcTemplate jdbc;
        @Autowired
        UserRepository userRepository;

        private Long newUser(String email) {
                return userRepository
                                .save(User.builder().email(email).password("hashed-password").role(Role.USER).build())
                                .getId();
        }

        @Test
        @DisplayName("[TC-DATA-105] 실제 MySQL 8.0에서 Flyway V1~V8이 모두 성공하고 Hibernate validate가 통과 (users.role은 ENUM)")
        void flywayAndValidate_passOnMySql() {
                List<String> versions = jdbc.queryForList(
                                "select version from flyway_schema_history where success = 1 order by installed_rank",
                                String.class);
                assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
                assertThat(jdbc.queryForObject("select data_type from information_schema.columns"
                                + " where table_schema = database() and table_name = 'users' and column_name = 'role'",
                                String.class))
                                .isEqualTo("enum");
        }

        @Test
        @DisplayName("[TC-DATA-106] MySQL(InnoDB)에서 없는 사용자 id 저장은 외래키로 거부, 사용자 삭제 시 계산·이용 기록이 함께 삭제 (#32)")
        void foreignKeys_workOnInnoDb() {
                Long userId = newUser("fk-mysql@test.com");
                jdbc.update("insert into loan_simulations (user_id, loan_amount, interest_rate) values (?, 300000000, 4.5)",
                                userId);
                jdbc.update("insert into user_activity_logs (user_id, action) values (?, 'LOAN_CALCULATION')", userId);

                assertThatThrownBy(() -> jdbc.update(
                                "insert into loan_simulations (user_id, loan_amount, interest_rate) values (987654, 1, 1)"))
                                .isInstanceOf(DataIntegrityViolationException.class);

                jdbc.update("delete from users where id = ?", userId);

                assertThat(jdbc.queryForObject("select count(*) from loan_simulations where user_id = ?", Long.class,
                                userId))
                                .isZero();
                assertThat(jdbc.queryForObject("select count(*) from user_activity_logs where user_id = ?", Long.class,
                                userId))
                                .isZero();
        }

        // V6 중복 이용 기록 삭제 SQL이 실제 MySQL에서 성공하고 짝이 있는 줄만 지움
        @Test
        @DisplayName("[TC-DATA-107] 실제 MySQL에서 V6의 중복 이용 기록 삭제(하위 쿼리를 파생 테이블로 감싼 DELETE)가 1093 오류 없이 짝이 있는 줄만 삭제 (#57)")
        void v6DuplicateDelete_worksOnMySql() throws Exception {
                String url = MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/v6check");
                try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                                Statement st = root.createStatement()) {
                        st.execute("create database v6check");
                }
                EncryptionUtil encryptionUtil = new EncryptionUtil();
                ReflectionTestUtils.setField(encryptionUtil, "encryptionKeyConfig", "HomeFin-AES-256-Key-Test-Padded!");
                V5__ProtectPersonalData v5 = new V5__ProtectPersonalData(encryptionUtil,
                                new EmailHasher("HomeFin-AES-256-Key-Test-Padded!"));
                Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("5").javaMigrations(v5).load()
                                .migrate();

                try (Connection conn = DriverManager.getConnection(url, "root", MYSQL.getPassword());
                                Statement st = conn.createStatement()) {
                        st.execute("insert into users (id, email, password, role) values (1, 'a', 'x', 'USER'), (2, 'b', 'x', 'USER')");
                        st.execute("insert into user_activity_logs (user_id, action, trace_id) values"
                                        + " (1, 'LOAN_CALCULATION', 't-analyze'), (1, 'UNIFIED_ANALYSIS', 't-analyze'),"
                                        + " (1, 'LOAN_CALCULATION', 't-loan-only'), (1, 'LOAN_CALCULATION', null),"
                                        + " (2, 'LOAN_CALCULATION', 't-analyze')");

                        Flyway.configure().dataSource(url, "root", MYSQL.getPassword()).target("6").javaMigrations(v5)
                                        .load()
                                        .migrate();

                        List<String> rows = new java.util.ArrayList<>();
                        try (var rs = st.executeQuery(
                                        "select user_id, action, trace_id from user_activity_logs order by id")) {
                                while (rs.next()) {
                                        rows.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3));
                                }
                        }
                        assertThat(rows).containsExactly("1:UNIFIED_ANALYSIS:t-analyze",
                                        "1:LOAN_CALCULATION:t-loan-only",
                                        "1:LOAN_CALCULATION:null", "2:LOAN_CALCULATION:t-analyze");
                }
        }
}
