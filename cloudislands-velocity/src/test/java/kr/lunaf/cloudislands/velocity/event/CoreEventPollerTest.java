package kr.lunaf.cloudislands.velocity.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.AdminEventQueryClient;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class CoreEventPollerTest {
    @Test
    void pollsTypedAdminEventsAndAdvancesSequence() {
        List<String> calls = new ArrayList<>();
        List<CoreEventEnvelope> received = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "adminEvents" -> typedEvents(calls);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );

        CoreEventPoller poller = new CoreEventPoller(raw, new CoreEventJsonCodec(), received::add, 2);
        poller.pollOnce();

        assertEquals(List.of("0:2"), calls);
        assertEquals(1, received.size());
        assertEquals(7L, received.get(0).sequence());
        assertEquals("NODE_DOWN", received.get(0).type());
        assertEquals("node-a", received.get(0).fields().get("nodeId"));
    }

    private static AdminEventQueryClient typedEvents(List<String> calls) {
        return new AdminEventQueryClient() {
            @Override
            public CompletableFuture<AdminEventStreamView> list(int limit) {
                throw new UnsupportedOperationException("list");
            }

            @Override
            public CompletableFuture<AdminEventStreamView> listSince(long sinceSeq, int limit) {
                calls.add(sinceSeq + ":" + limit);
                return CompletableFuture.completedFuture(new AdminEventStreamView(
                    1,
                    7,
                    List.of(new AdminEventView(7, "NODE_DOWN", Map.of("nodeId", "node-a", "state", "DOWN"), "2026-06-21T00:00:00Z"))
                ));
            }
        };
    }
}
