package store.one3d.production;

/**
 * The simulator could not be asked, or did not answer: connection refused,
 * timeout, lost reply or an unexpected response. Whether a start happened is
 * unknown; it must be reconciled, not assumed.
 */
class SimulatorUnavailableException extends Exception {

    SimulatorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    SimulatorUnavailableException(String message) {
        super(message);
    }
}
