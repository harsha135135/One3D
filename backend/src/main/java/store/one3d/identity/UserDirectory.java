package store.one3d.identity;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Local records of people who have signed in, keyed by (issuer, subject).
 *
 * <p>Uses {@code INSERT ... ON CONFLICT DO NOTHING} and then reads the row: two
 * first requests from the same new user at the same moment both end up with
 * the same id, and neither aborts the transaction it runs in.
 */
@Component
class UserDirectory {

    private final JdbcClient jdbc;

    UserDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID idFor(String issuer, String subject, String email) {
        jdbc.sql("""
                        INSERT INTO app_user (id, issuer, subject, email)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (issuer, subject) DO NOTHING
                        """)
                .params(UUID.randomUUID(), issuer, subject, email)
                .update();
        return jdbc.sql("SELECT id FROM app_user WHERE issuer = ? AND subject = ?")
                .params(issuer, subject)
                .query(UUID.class)
                .single();
    }
}
