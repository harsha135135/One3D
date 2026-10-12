package store.one3d.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the test infrastructure as real separate processes:
 * <ul>
 *   <li>a disposable PostgreSQL container; Spring Boot points the datasource
 *       at it (@ServiceConnection) and Flyway migrates the empty database;</li>
 *   <li>the printer simulator container ({@link SimulatorRig}), which replaces
 *       the configured simulator address;</li>
 *   <li>S3Mock as private, versioned file storage ({@link StorageRig}). The
 *       backend and the test's "browser" both use its address, so uploads go
 *       through the presigned URLs the API returns.</li>
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

    /** Replaces Razorpay: tests never call the real provider. */
    @Bean
    @Primary
    FakeRazorpay fakeRazorpay() {
        return new FakeRazorpay();
    }

    @Bean
    StorageRig storageRig() {
        return new StorageRig();
    }

    @Bean
    DynamicPropertyRegistrar storageAddress(StorageRig storage) {
        return registry -> {
            registry.add("one3d.storage.endpoint", storage::endpoint);
            registry.add("one3d.storage.public-endpoint", storage::endpoint);
        };
    }
}
