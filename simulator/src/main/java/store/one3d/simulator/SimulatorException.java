package store.one3d.simulator;

/** An expected error with the HTTP status and code the API returns for it. */
final class SimulatorException extends RuntimeException {

    private final int status;
    private final String code;

    SimulatorException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    int status() {
        return status;
    }

    String code() {
        return code;
    }
}
