package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRequestExecutor;
import org.junit.jupiter.api.Test;

class CoreLifecycleTest {
    @Test
    void lifecycleActionsStartForwardAndStopReverse() {
        List<String> calls = new ArrayList<>();
        List<CoreLifecycleAction> actions = List.of(
            action("httpRuntime", calls),
            action("backgroundTasks", calls)
        );

        CoreLifecycleActions.start(actions);
        CoreLifecycleActions.stop(actions);

        assertEquals(
            List.of("start:httpRuntime", "start:backgroundTasks", "stop:backgroundTasks", "stop:httpRuntime"),
            calls
        );
    }

    @Test
    void httpRuntimeStopsListenersBeforeExecutors() {
        CoreHttpRuntime runtime = new CoreHttpRuntime(
            new FakeHttpServer("server"),
            new FakeHttpServer("adminServer"),
            new CoreHttpRequestExecutor(1, 0, Duration.ofMillis(1)),
            new CoreHttpRequestExecutor(1, 0, Duration.ofMillis(1)),
            Duration.ZERO,
            null,
            null
        );

        assertEquals(List.of("httpExecutor", "adminHttpExecutor", "server", "adminServer"), runtime.startOrder());
        assertEquals(List.of("adminServer", "server", "adminHttpExecutor", "httpExecutor"), runtime.stopOrder());
        runtime.stop();
    }

    @Test
    void httpRuntimeStopCanRepeat() {
        FakeHttpServer server = new FakeHttpServer("server");
        CoreHttpRuntime runtime = new CoreHttpRuntime(
            server,
            null,
            new CoreHttpRequestExecutor(1, 0, Duration.ofMillis(1)),
            null,
            Duration.ZERO,
            null,
            null
        );

        runtime.stop();
        runtime.stop();

        assertEquals(List.of("stop:server:0", "stop:server:0"), server.calls);
    }

    private static CoreLifecycleAction action(String name, List<String> calls) {
        return new CoreLifecycleAction(
            name,
            () -> calls.add("start:" + name),
            () -> calls.add("stop:" + name)
        );
    }

    private static final class FakeHttpServer extends HttpServer {
        private final String name;
        private final List<String> calls = new ArrayList<>();
        private Executor executor;

        private FakeHttpServer(String name) {
            this.name = name;
        }

        @Override
        public void bind(InetSocketAddress address, int backlog) {
        }

        @Override
        public void start() {
            calls.add("start:" + name);
        }

        @Override
        public void setExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public void stop(int delay) {
            calls.add("stop:" + name + ":" + delay);
        }

        @Override
        public HttpContext createContext(String path, HttpHandler handler) {
            return null;
        }

        @Override
        public HttpContext createContext(String path) {
            return null;
        }

        @Override
        public void removeContext(String path) {
        }

        @Override
        public void removeContext(HttpContext context) {
        }

        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress("127.0.0.1", 0);
        }
    }
}
