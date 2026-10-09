package store.one3d.simulator;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point. Opens the journal, then serves HTTP until the process stops.
 * Everything the simulator decided survives a restart because it is in the journal.
 */
public final class SimulatorApp {

    private static final Logger log = System.getLogger(SimulatorApp.class.getName());

    private SimulatorApp() {
    }

    public static void main(String[] args) throws Exception {
        SimulatorConfig config = SimulatorConfig.fromEnvironment(System.getenv());
        Journal journal = new Journal(config.journalPath());
        HttpApi api = new HttpApi(new PrinterSimulator(journal, Clock.systemUTC()),
                config.bindAddress(), config.port(), config.devControls());
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            api.close();
            journal.close();
            stopped.countDown();
        }));
        api.start();
        log.log(Level.INFO, "Simulated printer listening on {0}:{1} (journal {2}, dev controls {3})",
                config.bindAddress(), String.valueOf(api.port()), config.journalPath(), config.devControls());
        stopped.await();
    }
}
