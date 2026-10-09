package store.one3d.production;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/** The simulator address from {@code one3d.simulator.base-url}. */
@Component
class ConfiguredSimulatorEndpoint implements SimulatorEndpoint {

    private final URI baseUri;

    ConfiguredSimulatorEndpoint(@Value("${one3d.simulator.base-url}") URI baseUri) {
        this.baseUri = baseUri;
    }

    @Override
    public URI baseUri() {
        return baseUri;
    }
}
