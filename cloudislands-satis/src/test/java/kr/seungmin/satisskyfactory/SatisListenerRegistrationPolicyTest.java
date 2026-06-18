package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisListenerRegistrationPolicyTest {
    @Test
    void disabledFeaturesUnregisterRuntimeListenersInsteadOfKeepingHandlersLive() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("if (!operationalFeatureEnabled(\"machines\"))"));
        assertTrue(source.contains("machineListenerRegistered = unregisterListener(machineListener, machineListenerRegistered);"));
        assertTrue(source.contains("machineListener = null;"));
        assertTrue(source.contains("} else if (!machineListenerRegistered)"));

        assertTrue(source.contains("if (!operationalFeatureEnabled(\"gui\"))"));
        assertTrue(source.contains("closeOpenFactoryGuis();"));
        assertTrue(source.contains("guiListenerRegistered = unregisterListener(guiListener, guiListenerRegistered);"));
        assertTrue(source.contains("guiListener = null;"));

        assertTrue(source.contains("if (!lifecycleListenerNeeded())"));
        assertTrue(source.contains("lifecycleListenerRegistered = unregisterListener(lifecycleListener, lifecycleListenerRegistered);"));
        assertTrue(source.contains("lifecycleListener = null;"));
        assertTrue(source.contains("this::dataWritesEnabled"));
    }

    @Test
    void lifecycleListenerRequiresLifecycleFeatureAndLifecycleState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private boolean lifecycleListenerNeeded()"));
        assertTrue(source.contains("return operationalFeatureEnabled(\"lifecycle\") && lifecycleStateEnabled();"));
    }
}
