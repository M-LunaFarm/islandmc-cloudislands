package kr.lunaf.cloudislands.coreservice.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;

public final class PrometheusMetricsRenderer {
    private final InMemoryNodeRegistry nodes;
    private final IslandJobQueue jobs;
    private final Duration heartbeatTimeout;

    public PrometheusMetricsRenderer(InMemoryNodeRegistry nodes, IslandJobQueue jobs, Duration heartbeatTimeout) {
        this.nodes = nodes;
        this.jobs = jobs;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        help(out, "cloudislands_nodes_online", "CloudIslands nodes with fresh heartbeat");
        type(out, "cloudislands_nodes_online", "gauge");
        help(out, "cloudislands_node_players", "Players currently reported by a node");
        type(out, "cloudislands_node_players", "gauge");
        help(out, "cloudislands_node_mspt", "Node MSPT reported by Paper heartbeat");
        type(out, "cloudislands_node_mspt", "gauge");
        help(out, "cloudislands_node_active_islands", "Active islands currently reported by a node");
        type(out, "cloudislands_node_active_islands", "gauge");
        help(out, "cloudislands_node_activation_queue", "Activation queue depth currently reported by a node");
        type(out, "cloudislands_node_activation_queue", "gauge");
        help(out, "cloudislands_node_heap_used_mb", "Node JVM heap used in MiB");
        type(out, "cloudislands_node_heap_used_mb", "gauge");
        help(out, "cloudislands_node_state", "Node state marker by state label");
        type(out, "cloudislands_node_state", "gauge");
        Instant now = Instant.now();
        for (NodeLoad node : nodes.snapshot()) {
            boolean fresh = Duration.between(node.lastHeartbeat(), now).compareTo(heartbeatTimeout) <= 0;
            labels(out, "cloudislands_nodes_online", node, null).append(fresh && node.state() != NodeState.DOWN ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_players", node, null).append(node.players()).append('\n');
            labels(out, "cloudislands_node_mspt", node, null).append(node.mspt()).append('\n');
            labels(out, "cloudislands_node_active_islands", node, null).append(node.activeIslands()).append('\n');
            labels(out, "cloudislands_node_activation_queue", node, null).append(node.activationQueue()).append('\n');
            labels(out, "cloudislands_node_heap_used_mb", node, null).append(node.heapUsedMb()).append('\n');
            for (NodeState state : NodeState.values()) {
                labels(out, "cloudislands_node_state", node, "state=\"" + state.name() + "\"").append(node.state() == state ? 1 : 0).append('\n');
            }
        }
        help(out, "cloudislands_jobs_total", "Island jobs by in-memory state or backend mode");
        type(out, "cloudislands_jobs_total", "gauge");
        if (jobs instanceof InMemoryIslandJobPublisher memoryJobs) {
            for (Map.Entry<String, Long> entry : memoryJobs.countsByState().entrySet()) {
                out.append("cloudislands_jobs_total{state=\"").append(entry.getKey()).append("\",backend=\"memory\"} ").append(entry.getValue()).append('\n');
            }
        } else {
            out.append("cloudislands_jobs_total{state=\"backend_external\",backend=\"redis\"} 1\n");
        }
        return out.toString();
    }

    private static void help(StringBuilder out, String name, String description) {
        out.append("# HELP ").append(name).append(' ').append(description).append('\n');
    }

    private static void type(StringBuilder out, String name, String type) {
        out.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private static StringBuilder labels(StringBuilder out, String name, NodeLoad node, String extra) {
        out.append(name).append("{node=\"").append(escape(node.nodeId())).append("\",server=\"").append(escape(node.velocityServerName())).append("\"");
        if (extra != null && !extra.isBlank()) {
            out.append(',').append(extra);
        }
        return out.append("} ");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
