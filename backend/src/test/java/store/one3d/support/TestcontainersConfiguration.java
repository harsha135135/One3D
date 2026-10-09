package store.one3d.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the test infrastructure as real separate processes:
 * <ul>
 *   <li>a disposable PostgreSQL container; Spring Boot points the datasource
 *       at it (@ServiceConnection) and Flyway migrates the empty database;</li>
 *   <li>the printer simulator container ({@link SimulatorRig}), which replaces
 *       the configured simulator address.</li>
 * </ul>
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

    @Bean
    @Primary
    SimulatorRig simulatorRig() {
        return new SimulatorRig();
    }
}
