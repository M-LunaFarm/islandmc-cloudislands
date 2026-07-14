package kr.lunaf.cloudislands.paper.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperBootstrapRecoveryPolicyTest {
    @Test
    void fallbackCommandsExistBeforeRuntimeBootstrapAndRemainAfterFailure() throws Exception {
        Path root = Path.of("src/main/java/kr/lunaf/cloudislands/paper");
        String plugin = Files.readString(root.resolve("CloudIslandsPaperPlugin.java"));
        String fallback = Files.readString(root.resolve("command/PaperBootstrapStatusCommand.java"));

        assertTrue(plugin.indexOf("PaperBootstrapStatusCommand.install(this, bootstrapStatus)") < plugin.indexOf("new PaperPluginBootstrap(this).enable()"));
        assertTrue(plugin.contains("catch (RuntimeException | LinkageError failure)"));
        assertTrue(plugin.contains("stopRuntimeState()"));
        assertTrue(plugin.contains("HandlerList.unregisterAll(this)"));
        assertTrue(plugin.contains("cancelTasks(this)"));
        assertTrue(plugin.contains("unregisterOutgoingPluginChannel(this)"));
        assertTrue(fallback.contains("plugin.retryBootstrap()"));
        assertTrue(fallback.contains("CloudIslands bootstrap="));
        assertTrue(fallback.contains("gameplay is safely unavailable"));
    }

    @Test
    void startupRejectionsEnterDiagnosticModeInsteadOfDisablingThePlugin() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String services = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/bootstrap/PaperRuntimeServices.java"));

        assertTrue(bootstrap.contains("throw new PaperBootstrapException"));
        assertFalse(bootstrap.contains("disablePlugin(plugin)"));
        assertTrue(services.contains("catch (RuntimeException | LinkageError failure)"));
        assertTrue(services.contains("services.stop()"));
        assertTrue(services.contains("failure.addSuppressed(cleanupFailure)"));
    }
}
