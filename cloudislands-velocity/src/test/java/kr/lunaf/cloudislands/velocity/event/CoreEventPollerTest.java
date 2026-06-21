package kr.lunaf.cloudislands.velocity.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreAdminEventQueryClient;
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
                case "adminEvents" -> new CoreAdminEventQueryClient((CoreApiClient) proxy);
                case "listEventsSince" -> {
                    calls.add(args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"oldestSeq":1,"latestSeq":7,"events":[
                          {"seq":7,"type":"NODE_DOWN","fields":{"nodeId":"node-a","state":"DOWN"},"occurredAt":"2026-06-21T00:00:00Z"}
                        ]}
                        """);
                }
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
}
