package kr.lunaf.cloudislands.paper.heartbeat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperHeartbeatServicePolicyTest {
    @Test
    void heartbeatUsesGlobalBukkitReadsAndObservesAsyncCoreFailure() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/heartbeat/PaperHeartbeatService.java"));

        assertTrue(
            source.contains("task = scheduler.repeatGlobal(interval, interval, this::publish)"),
            "heartbeat snapshots read Bukkit state and must run on the global thread"
        );
        assertTrue(
            source.contains("runtimeCommands.publishHeartbeat(heartbeat).whenComplete"),
            "heartbeat publishing must observe the asynchronous HTTP result without blocking the Bukkit thread"
        );
        assertTrue(
            !source.contains("publishHeartbeat(heartbeat).join()"),
            "heartbeat HTTP must never block plugin enable, tick, or disable threads"
        );
        assertTrue(
            source.contains("logHeartbeatFailure(cause)"),
            "failed heartbeat results must remain rate-limited and operator-visible"
        );
    }

    @Test
    void gracefulStopPublishesShutdownBeforeCoreCanRouteMoreWork() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/heartbeat/PaperHeartbeatService.java"));
        int stop = source.indexOf("public void stop()");
        int cancel = source.indexOf("cancelScheduledHeartbeat();", stop);
        int shutdown = source.indexOf("publish(NodeState.SHUTTING_DOWN);", stop);

        assertTrue(stop >= 0 && cancel > stop && shutdown > cancel, "stop must cancel periodic READY heartbeats before publishing SHUTTING_DOWN");
        assertTrue(source.contains("public void start(long intervalTicks) {\n        cancelScheduledHeartbeat();"), "restart must not emit a false SHUTTING_DOWN heartbeat");
    }

    @Test
    void heartbeatStartsOnlyAfterIslandWorkerIsReadyToAcceptClaims() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        int islandRuntime = bootstrap.indexOf("PaperIslandNodeRuntime.start(");
        int heartbeat = bootstrap.indexOf("PaperHeartbeatRuntime.start(");

        assertTrue(islandRuntime >= 0 && heartbeat > islandRuntime, "the node must not advertise STARTING or READY before its island worker is installed");
        assertTrue(bootstrap.contains("storageHealth::available"), "heartbeat must read the async storage-health cache");
        assertTrue(!bootstrap.contains("storage.available()"), "plugin bootstrap and heartbeat paths must never probe object storage on the Bukkit thread");
    }
}
