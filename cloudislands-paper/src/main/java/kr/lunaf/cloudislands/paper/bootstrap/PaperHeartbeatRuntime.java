package kr.lunaf.cloudislands.paper.bootstrap;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.heartbeat.PaperHeartbeatService;

public final class PaperHeartbeatRuntime implements RuntimeComponent {
    private final PaperHeartbeatService service;

    private PaperHeartbeatRuntime(PaperHeartbeatService service) {
        this.service = service;
    }

    public static PaperHeartbeatRuntime start(
            CloudIslandsPaperPlugin plugin,
            CoreApiClient client,
            String nodeId,
            String pool,
            String velocityServerName,
            String supportedTemplates,
            Supplier<String> supportedTemplatesSupplier,
            BooleanSupplier storageAvailable,
            IntSupplier activeIslandCount,
            IntSupplier activationQueue,
            IntSupplier recentFailurePenalty) {
        int maxActivationQueue = Math.max(1, plugin.getConfig().getInt("node.max-activation-queue", plugin.getConfig().getInt("island-node.activation.max-concurrent", 4)));
        int hardPlayerCap = Math.max(1, plugin.getConfig().getInt("node.hard-player-cap", 110));
        int reservedSlots = Math.max(0, plugin.getConfig().getInt("node.reserved-slots", 15));
        int reservedSoftCap = Math.max(1, hardPlayerCap - reservedSlots);
        int softPlayerCap = plugin.getConfig().contains("node.soft-player-cap")
            ? Math.max(1, Math.min(reservedSoftCap, plugin.getConfig().getInt("node.soft-player-cap", reservedSoftCap)))
            : reservedSoftCap;
        int maxActiveIslands = Math.max(1, plugin.getConfig().getInt("node.max-active-islands", 600));
        PaperHeartbeatService service = new PaperHeartbeatService(
            plugin,
            client,
            nodeId,
            pool,
            velocityServerName,
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
        service.start(plugin.getConfig().getLong("heartbeat.interval-ticks", 20L));
        return new PaperHeartbeatRuntime(service);
    }

    @Override
    public void stop() {
        service.stop();
    }
}
