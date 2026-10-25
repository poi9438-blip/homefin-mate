package com.homefinmate.migration;

import com.homefinmate.util.EmailHasher;
import com.homefinmate.util.EncryptionUtil;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// 개인정보 보호 (#31, #33): 암호화 키가 필요해서 SQL이 아닌 Java 마이그레이션 (Spring이 키를 주입한 Bean으로 실행)
// 1) loan_simulations.dti → dti_encrypted (AES-GCM 암호문), 평문 dti 컬럼 삭제
// 2) login_failure_logs.email → email_hash (HMAC)
// 3) audit_logs의 사용자 actor_id(이메일) → "user:{id}", 사용자를 찾을 수 없으면 "user:deleted"
@Component
public class V5__ProtectPersonalData extends BaseJavaMigration {

    private static final int BATCH_SIZE = 1000;

    private final EncryptionUtil encryptionUtil;
    private final EmailHasher emailHasher;

    public V5__ProtectPersonalData(EncryptionUtil encryptionUtil, EmailHasher emailHasher) {
        this.encryptionUtil = encryptionUtil;
        this.emailHasher = emailHasher;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        encryptDti(conn);
        hashLoginFailureEmails(conn);
        replaceAuditActorEmails(conn);
    }

    private void encryptDti(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("alter table loan_simulations add column dti_encrypted varchar(255)");
        }
        List<Long> ids = new ArrayList<>();
        List<String> values = new ArrayList<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("select id, dti from loan_simulations where dti is not null")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
                values.add(encryptionUtil.encrypt(rs.getBigDecimal(2).toPlainString()));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("update loan_simulations set dti_encrypted = ? where id = ?")) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(1, values.get(i));
                ps.setLong(2, ids.get(i));
                ps.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        try (Statement st = conn.createStatement()) {
            st.execute("alter table loan_simulations drop column dti");
        }
    }

    private void hashLoginFailureEmails(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("alter table login_failure_logs rename column email to email_hash");
        }
        List<Long> ids = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "select id, email_hash from login_failure_logs where email_hash not like 'hmac:%'")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
                hashes.add(emailHasher.hash(rs.getString(2)));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("update login_failure_logs set email_hash = ? where id = ?")) {
            for (int i = 0; i < ids.size(); i++) {
                ps.setString(1, hashes.get(i));
                ps.setLong(2, ids.get(i));
                ps.addBatch();
                if ((i + 1) % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void replaceAuditActorEmails(Connection conn) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("update audit_logs set actor_id = (select concat('user:', u.id) from users u"
                    + " where u.email = audit_logs.actor_id)"
                    + " where actor_type = 'USER' and actor_id in (select email from users)");
            st.executeUpdate("update audit_logs set actor_id = 'user:deleted'"
                    + " where actor_type = 'USER' and actor_id not like 'user:%'");
        }
    }
}
