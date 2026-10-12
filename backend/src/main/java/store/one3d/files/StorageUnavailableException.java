package store.one3d.files;

/** Storage could not be reached or answered with an unexpected error. */
class StorageUnavailableException extends RuntimeException {

    StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
