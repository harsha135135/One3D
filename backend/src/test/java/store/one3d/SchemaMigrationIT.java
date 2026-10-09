package store.one3d;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import store.one3d.support.PostgresIntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test container starts empty, so a running context proves that the
 * migrations build the schema from scratch and that the JPA entities match it
 * (spring.jpa.hibernate.ddl-auto=validate fails startup otherwise).
 */
@PostgresIntegrationTest
class SchemaMigrationIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void freshDatabaseIsBuiltOnlyByTheVersionedMigrations() {
        List<String> applied = jdbc.sql("""
                        SELECT version FROM flyway_schema_history
                        WHERE success ORDER BY installed_rank
                        """)
                .query(String.class)
                .list();

        assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6");
    }

    @Test
    void seedAddsOneSimulatedPrinterWithAClearBed() {
        String bedState = jdbc.sql("""
                        SELECT bed_state FROM printer
                        WHERE id = '0d3d0000-0000-4000-8000-000000000101' AND simulated
                        """)
                .query(String.class)
                .single();

        assertThat(bedState).isEqualTo("CLEAR");
    }

    @Test
    void seedApprovesRecipeRevisionOneForTheDemoConfiguration() {
        Integer revision = jdbc.sql("""
                        SELECT r.revision_number
                        FROM product_configuration c
                        JOIN recipe_revision r ON r.id = c.current_recipe_revision_id
                        WHERE c.code = 'HEX-ORG-PLA-BLACK'
                        """)
                .query(Integer.class)
                .single();

        assertThat(revision).isEqualTo(1);
    }
}
