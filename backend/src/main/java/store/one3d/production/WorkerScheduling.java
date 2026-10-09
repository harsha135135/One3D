package store.one3d.production;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the dispatch worker and the observer on a timer. Disabled with
 * {@code one3d.worker.enabled=false}; integration tests do that and call
 * {@link DispatchWorker#runOnce()} and {@link ExecutionObserver#observeOnce()}
 * themselves, so background threads cannot pick up rows other tests create.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "one3d.worker.enabled", havingValue = "true")
class WorkerScheduling {

    private final DispatchWorker worker;
    private final ExecutionObserver observer;

    WorkerScheduling(DispatchWorker worker, ExecutionObserver observer) {
        this.worker = worker;
        this.observer = observer;
    }

    @Scheduled(fixedDelayString = "${one3d.worker.poll-interval}")
    void tick() {
        worker.runOnce();
        observer.observeOnce();
    }
}
