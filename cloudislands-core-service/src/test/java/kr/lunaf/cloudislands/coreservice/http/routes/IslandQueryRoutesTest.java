package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpHandler;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class IslandQueryRoutesTest {
    @Test
    void registersQueryPrefixesWithExplicitMethods() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandQueryRoutes(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("GET", "DELETE"), registry.methods("/v1/islands/"));
        assertEquals(Set.of("GET"), registry.methods("/v1/players/"));
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
