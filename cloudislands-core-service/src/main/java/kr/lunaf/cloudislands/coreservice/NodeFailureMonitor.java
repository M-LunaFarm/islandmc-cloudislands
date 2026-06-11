package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;

public final class NodeFailureMonitor {
    private final NodeRegistry nodes;
    private final IslandRuntimeRepository runtimes;
    private final GlobalEventPublisher events;
    private final Duration heartbeatTimeout;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public NodeFailureMonitor(NodeRegistry nodes, IslandRuntimeRepository runtimes, GlobalEventPublisher events, Duration heartbeatTimeout) {
        this.nodes = nodes;
        this.runtimes = runtimes;
        this.events = events;
        this.heartbeatTimeout = heartbeatTimeout;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudislands-node-failure-monitor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long delayMillis = Math.max(1000L, heartbeatTimeout.toMillis());
        executor.scheduleWithFixedDelay(this::sweep, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void sweep() {
        List<String> downNodes = nodes.markStaleDown(heartbeatTimeout);
        for (String nodeId : downNodes) {
            int affected = runtimes.markRecoveryRequiredForNode(nodeId);
            events.publish("NODE_DOWN", Map.of("nodeId", nodeId, "recoveryRequired", Integer.toString(affected)));
        }
    }
}
