package store.one3d.simulator;

import java.nio.file.Path;
import java.util.Map;

/**
 * Settings read from environment variables.
 *
 * @param bindAddress interface to listen on; 0.0.0.0 inside a container, where
 *                    Docker decides what is published
 * @param devControls whether the /dev endpoints (complete, fail, inject faults)
 *                    exist; enable only for local development and tests
 */
record SimulatorConfig(String bindAddress, int port, Path journalPath, boolean devControls) {

    static SimulatorConfig fromEnvironment(Map<String, String> env) {
        return new SimulatorConfig(
                env.getOrDefault("SIMULATOR_BIND", "127.0.0.1"),
                Integer.parseInt(env.getOrDefault("SIMULATOR_PORT", "8090")),
                Path.of(env.getOrDefault("SIMULATOR_JOURNAL", "data/journal.db")),
                Boolean.parseBoolean(env.getOrDefault("SIMULATOR_DEV_CONTROLS", "false")));
    }
}
