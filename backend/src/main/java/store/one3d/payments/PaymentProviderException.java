package store.one3d.payments;

/** The provider could not be reached or answered unexpectedly. Never carries keys or customer data. */
public class PaymentProviderException extends Exception {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
