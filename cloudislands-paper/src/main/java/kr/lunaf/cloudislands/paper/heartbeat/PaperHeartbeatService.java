package kr.lunaf.cloudislands.paper.heartbeat;

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
    private BukkitTask task;

    public PaperHeartbeatService(Plugin plugin, CoreApiClient coreApiClient, String nodeId, String pool, String velocityServerName) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId;
        this.pool = pool;
        this.velocityServerName = velocityServerName;
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
            heapMax
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
