package kr.lunaf.cloudislands.paper.bootstrap;

import java.util.function.Supplier;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.health.PaperHealthService;

public final class PaperHealthRuntime implements RuntimeComponent {
    private final PaperHealthService service;

    private PaperHealthRuntime(PaperHealthService service) {
        this.service = service;
    }

    public static PaperHealthRuntime startIfEnabled(
            CloudIslandsPaperPlugin plugin,
            PaperRuntimeConfig.Health config,
            Supplier<String> healthJson,
            Supplier<String> metricsText) {
        if (!config.enabled()) {
            return null;
        }
        PaperHealthService service = new PaperHealthService(
            plugin,
            config.bindHost(),
            config.port(),
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
