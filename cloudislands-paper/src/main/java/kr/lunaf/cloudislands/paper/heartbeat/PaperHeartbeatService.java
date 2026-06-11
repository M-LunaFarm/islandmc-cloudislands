package kr.lunaf.cloudislands.paper.heartbeat;

import java.util.function.BooleanSupplier;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperHeartbeatService {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final String pool;
    private final String velocityServerName;
    private final String supportedTemplates;
    private final BooleanSupplier storageAvailable;
    private BukkitTask task;

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName) {
        this(plugin, coreApiClient, nodeId, pool, velocityServerName, "*", () -> true);
    }

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName, BooleanSupplier storageAvailable) {
        this(plugin, coreApiClient, nodeId, pool, velocityServerName, "*", storageAvailable);
    }

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName, String supportedTemplates, BooleanSupplier storageAvailable) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
        this.pool = pool;
        this.velocityServerName = velocityServerName;
        this.supportedTemplates = supportedTemplates == null || supportedTemplates.isBlank() ? "*" : supportedTemplates;
        this.storageAvailable = storageAvailable;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::publish, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void publish() {
        Runtime runtime = Runtime.getRuntime();
        long heapMax = runtime.maxMemory() / 1024L / 1024L;
        long heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        NodeHeartbeatRequest heartbeat = new NodeHeartbeatRequest(
            nodeId,
            pool,
            velocityServerName,
            NodeState.READY,
            Bukkit.getOnlinePlayers().size(),
            0,
            currentMspt(),
            0,
            heapUsed,
            heapMax,
            storageAvailable.getAsBoolean(),
            supportedTemplates
        );
        coreApiClient.publishHeartbeat(heartbeat);
    }

    private double currentMspt() {
        double[] recent = Bukkit.getServer().getTPS();
        if (recent.length == 0 || recent[0] <= 0.0D) {
            return 50.0D;
        }
        return Math.min(50.0D, 1000.0D / recent[0]);
    }
}
