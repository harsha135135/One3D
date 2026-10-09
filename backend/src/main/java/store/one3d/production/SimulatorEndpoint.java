package store.one3d.production;

import java.net.URI;

/**
 * Where the printer simulator is reachable. Read on every call, so the address
 * can change while the backend runs (tests restart the simulator on a new port).
 */
public interface SimulatorEndpoint {

    URI baseUri();
}
