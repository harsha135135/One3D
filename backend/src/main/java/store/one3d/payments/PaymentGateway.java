package store.one3d.payments;

import java.time.Instant;
import java.util.List;

/**
 * The payment provider, as the application needs it: create a provider order
 * for a checkout, and ask which payments were made against it. Implemented by
 * {@link RazorpayGateway}; tests use an in-memory fake.
 */
public interface PaymentGateway {

    /** A provider order (Razorpay: {@code order_…}). */
    record ProviderOrder(String id, long amountMinor, String currency) {
    }

    /** A payment as the provider reports it. Only {@code status = captured} means the money was taken. */
    record ProviderPayment(String id, String orderId, long amountMinor, String currency, String status,
                           Instant createdAt) {

        public boolean captured() {
            return "captured".equals(status);
        }
    }

    /** Creates a provider order that captures automatically once the customer pays. */
    ProviderOrder createOrder(String receipt, long amountMinor, String currency) throws PaymentProviderException;

    List<ProviderPayment> paymentsOf(String providerOrderId) throws PaymentProviderException;
}
