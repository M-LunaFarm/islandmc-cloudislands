package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import org.junit.jupiter.api.Test;

class EventRoutesTest {
    @Test
    void registersEventEndpointAsRouteGroup() {
        EventRoutes routes = new EventRoutes(new InMemoryGlobalEventPublisher());

        assertDoesNotThrow(() -> routes.register((path, handler) -> {
            if (!path.equals("/v1/events")) {
                throw new IllegalArgumentException(path);
            }
        }));
    }
}
