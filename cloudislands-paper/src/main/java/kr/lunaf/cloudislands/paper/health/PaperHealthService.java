package kr.lunaf.cloudislands.paper.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

public final class PaperHealthService {
    private final Plugin plugin;
    private final String bindHost;
    private final int port;
    private final Supplier<String> healthJson;
    private final Supplier<String> metricsText;
    private HttpServer server;

    public PaperHealthService(Plugin plugin, String bindHost, int port, Supplier<String> healthJson, Supplier<String> metricsText) {
        this.plugin = plugin;
        this.bindHost = bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost;
        this.port = port;
        this.healthJson = healthJson;
        this.metricsText = metricsText;
    }

    public void start() {
        stop();
        if (port <= 0) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
            server.createContext("/health", exchange -> respond(exchange, "application/json", healthJson.get()));
            server.createContext("/metrics", exchange -> respond(exchange, "text/plain; version=0.0.4", metricsText.get()));
            server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "cloudislands-paper-health");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        } catch (IOException exception) {
            server = null;
            plugin.getLogger().warning("CloudIslands health endpoint failed to start: " + exception.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
