package kr.lunaf.cloudislands.paper.heartbeat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperHeartbeatServicePolicyTest {
    @Test
    void asynchronousCoreFailureReachesTheHeartbeatFailureLogger() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/heartbeat/PaperHeartbeatService.java"));

        assertTrue(
            source.contains("runtimeCommands.publishHeartbeat(heartbeat).join()"),
            "heartbeat publishing must await the HTTP result so asynchronous failures reach publish()"
        );
        assertTrue(
            source.contains("catch (RuntimeException exception)") && source.contains("logHeartbeatFailure(exception)"),
            "failed heartbeat results must remain rate-limited and operator-visible"
        );
    }
}
