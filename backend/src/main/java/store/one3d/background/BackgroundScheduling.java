package store.one3d.background;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Runs every {@link BackgroundJob} on a timer, one after another. Disabled with
 * {@code one3d.worker.enabled=false}; integration tests do that and call
 * {@code runOnce()} themselves, so background threads cannot pick up rows
 * other tests create.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "one3d.worker.enabled", havingValue = "true")
class BackgroundScheduling {

    private static final Logger log = LoggerFactory.getLogger(BackgroundScheduling.class);

    private final List<BackgroundJob> jobs;

    BackgroundScheduling(List<BackgroundJob> jobs) {
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${one3d.worker.poll-interval}")
    void tick() {
        for (BackgroundJob job : jobs) {
            try {
                job.runOnce();
            } catch (RuntimeException e) {
                // One failing job must not stop the others; its work stays queued.
                log.error("Background job {} failed", job.getClass().getSimpleName(), e);
            }
        }
    }
}
