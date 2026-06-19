package kr.lunaf.cloudislands.paper.bootstrap;

import java.util.function.Supplier;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.health.PaperHealthService;

public final class PaperHealthRuntime implements RuntimeComponent {
    private final PaperHealthService service;

    private PaperHealthRuntime(PaperHealthService service) {
        this.service = service;
    }

    public static PaperHealthRuntime startIfEnabled(
            CloudIslandsPaperPlugin plugin,
            Supplier<String> healthJson,
            Supplier<String> metricsText) {
        if (!plugin.getConfig().getBoolean("health.enabled", false)) {
            return null;
        }
        PaperHealthService service = new PaperHealthService(
            plugin,
            plugin.getConfig().getString("health.bind-host", "127.0.0.1"),
            plugin.getConfig().getInt("health.port", 8787),
            healthJson,
            metricsText
        );
        service.start();
        return new PaperHealthRuntime(service);
    }

    @Override
    public void stop() {
        service.stop();
    }
}
