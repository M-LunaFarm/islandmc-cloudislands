package kr.lunaf.cloudislands.paper.platform.compatibility;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperRuntimeSelectionPolicyTest {
    @Test
    void bootstrapSelectsPaperAdapterBeforeRuntimeServicesStart() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));

        assertTrue(bootstrap.contains("PaperRuntimeCompatibility.selectCurrent(PaperVersionAdapterRegistry.defaults())"));
        assertTrue(bootstrap.contains("plugin.runtimeCompatibility = runtimeCompatibility"));
        assertTrue(bootstrap.contains("disablePlugin(plugin)"));
        assertTrue(bootstrap.indexOf("PaperRuntimeCompatibility.selectCurrent") < bootstrap.indexOf("PaperRuntimeConfigLoader.load"));
        assertTrue(bootstrap.indexOf("PaperRuntimeCompatibility.selectCurrent") < bootstrap.indexOf("PaperRuntimeServices.register"));
    }

    @Test
    void observabilityAndDiagnosticsExposeSelectedPaperAdapter() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/CloudIslandsPaperPlugin.java"));
        String observability = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperObservabilityFormatter.java"));
        String admin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(plugin.contains("PaperRuntimeCompatibility.RuntimeSelection runtimeCompatibility"));
        assertTrue(plugin.contains("runtimeCompatibility()"));
        assertTrue(observability.contains("paperAdapterId"));
        assertTrue(observability.contains("cloudislands_paper_version_adapter"));
        assertTrue(observability.contains(";paperAdapterId="));
        assertTrue(admin.contains("runtimeCompatibilityDiagnosticSection()"));
        assertTrue(admin.contains("plugin.runtimeCompatibility().diagnosticsSection()"));
    }
}
