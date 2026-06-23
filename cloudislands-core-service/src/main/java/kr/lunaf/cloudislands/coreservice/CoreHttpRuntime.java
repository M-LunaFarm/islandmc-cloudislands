package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRequestExecutor;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRouteRegistrar;

public record CoreHttpRuntime(
    HttpServer server,
    HttpServer adminServer,
    CoreHttpRequestExecutor httpExecutor,
    CoreHttpRequestExecutor adminHttpExecutor,
    Duration httpShutdownGrace,
    CoreHttpRouteRegistrar routeRegistrar,
    CoreHttpRouteRegistrar adminRouteRegistrar
) {
    void start() {
        CoreLifecycleActions.start(actions());
    }

    void stop() {
        CoreLifecycleActions.stop(actions());
    }

    List<String> startOrder() {
        return CoreLifecycleActions.startOrder(actions());
    }

    List<String> stopOrder() {
        return CoreLifecycleActions.stopOrder(actions());
    }

    private List<CoreLifecycleAction> actions() {
        List<CoreLifecycleAction> actions = new ArrayList<>();
        actions.add(new CoreLifecycleAction(
            "httpExecutor",
            CoreHttpRuntime::noop,
            () -> httpExecutor.shutdownGracefully(httpShutdownGrace)
        ));
        if (adminHttpExecutor != null) {
            actions.add(new CoreLifecycleAction(
                "adminHttpExecutor",
                CoreHttpRuntime::noop,
                () -> adminHttpExecutor.shutdownGracefully(httpShutdownGrace)
            ));
        }
        actions.add(new CoreLifecycleAction(
            "server",
            server::start,
            () -> server.stop(shutdownDelaySeconds())
        ));
        if (adminServer != null) {
            actions.add(new CoreLifecycleAction(
                "adminServer",
                adminServer::start,
                () -> adminServer.stop(shutdownDelaySeconds())
            ));
        }
        return List.copyOf(actions);
    }

    private int shutdownDelaySeconds() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, httpShutdownGrace.toSeconds()));
    }

    private static void noop() {
    }
}
