package kr.lunaf.cloudislands.paper.bootstrap;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.heartbeat.PaperHeartbeatService;

public final class PaperHeartbeatRuntime implements RuntimeComponent {
    private final PaperHeartbeatService service;

    private PaperHeartbeatRuntime(PaperHeartbeatService service) {
        this.service = service;
    }

    public static PaperHeartbeatRuntime start(
            CloudIslandsPaperPlugin plugin,
            CoreApiClient client,
            PaperRuntimeConfig config,
            String supportedTemplates,
            Supplier<String> supportedTemplatesSupplier,
            BooleanSupplier storageAvailable,
            IntSupplier activeIslandCount,
            IntSupplier activationQueue,
            IntSupplier recentFailurePenalty) {
        int maxActivationQueue = config.node().maxActivationQueue();
        int hardPlayerCap = config.node().hardPlayerCap();
        int reservedSlots = config.node().reservedSlots();
        int softPlayerCap = config.node().effectiveSoftPlayerCap();
        int maxActiveIslands = config.node().maxActiveIslands();
        PaperHeartbeatService service = new PaperHeartbeatService(
            plugin,
            client,
            config.node().id(),
            config.node().pool(),
            config.node().velocityServerName(),
            plugin.getDescription().getVersion(),
            supportedTemplates,
            supportedTemplatesSupplier,
            storageAvailable,
            () -> softPlayerCap,
            () -> hardPlayerCap,
            () -> reservedSlots,
            activeIslandCount,
            () -> maxActiveIslands,
            activationQueue,
            () -> maxActivationQueue,
            () -> Math.min(1.5D, (double) activeIslandCount.getAsInt() / maxActiveIslands),
            recentFailurePenalty
        );
        service.start(config.heartbeat().intervalTicks());
        return new PaperHeartbeatRuntime(service);
    }

    @Override
    public void stop() {
        service.stop();
    }
}
