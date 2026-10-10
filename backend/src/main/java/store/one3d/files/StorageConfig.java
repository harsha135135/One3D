package store.one3d.files;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

/**
 * The S3 clients. The backend talks to storage at {@code endpoint}; presigned
 * URLs are built for {@code public-endpoint}, the address the browser uses.
 * Both are empty on AWS, where the SDK uses S3 itself.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .forcePathStyle(properties.pathStyle())
                // Checksums only where S3 requires them, so a plain browser PUT
                // to a presigned URL matches what was signed.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                // Bounded waits: a stuck call fails and the work is retried later.
                .overrideConfiguration(c -> c.apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofSeconds(60)));
        properties.endpointUri().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
        properties.publicEndpointUri().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties properties) {
        return properties.hasStaticCredentials()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()))
                : DefaultCredentialsProvider.builder().build();
    }
}
