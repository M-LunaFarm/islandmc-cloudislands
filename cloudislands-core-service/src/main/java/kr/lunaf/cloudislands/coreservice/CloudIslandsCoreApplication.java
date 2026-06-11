package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;

public final class CloudIslandsCoreApplication {
    private final HttpServer server;

    public CloudIslandsCoreApplication(int port) throws IOException {
        InMemoryNodeRegistry nodes = new InMemoryNodeRegistry();
        RoutingOrchestrator routing = new RoutingOrchestrator(nodes, new NodeAllocator(Duration.ofSeconds(5)));
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/health", exchange -> write(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/v1/nodes", exchange -> write(exchange, 200, nodes.toJson()));
        server.createContext("/v1/routes/home", exchange -> write(exchange, 202, routing.prepareHomeRouteJson()));
        server.createContext("/v1/routes/visit", exchange -> write(exchange, 202, routing.prepareVisitRouteJson()));
        server.createContext("/v1/routes/consume", exchange -> write(exchange, 200, ""));
        server.createContext("/v1/nodes/heartbeat", exchange -> write(exchange, 202, "{\"accepted\":true}"));
    }

    public void start() {
        server.start();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8443 : Integer.parseInt(args[0]);
        new CloudIslandsCoreApplication(port).start();
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of("application/json; charset=utf-8"));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
