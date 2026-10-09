package store.one3d.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a disposable PostgreSQL container for the test run. Spring Boot
 * points the datasource at it (@ServiceConnection), and Flyway migrates the
 * empty database exactly as it would a new real one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Same major version as compose.yaml. */
    public static final String POSTGRES_IMAGE = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }
}
