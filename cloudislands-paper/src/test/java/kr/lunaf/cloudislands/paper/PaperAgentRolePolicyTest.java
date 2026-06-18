package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaperAgentRolePolicyTest {
    @Test
    void pinsCommonPaperAgentCapabilities() {
        assertEquals(
            List.of(
                "core-api-client",
                "redis-client",
                "config-loader",
                "message-renderer",
                "translation-manager",
                "permission-hook",
                "placeholder-hook",
                "metrics-exporter",
                "health-check-endpoint",
                "local-cache-manager"
            ),
            PaperAgentRolePolicy.commonCapabilities()
        );
        assertTrue(PaperAgentRolePolicy.commonCapability("core-api-client"));
        assertTrue(PaperAgentRolePolicy.commonCapability(" METRICS-EXPORTER "));
        assertFalse(PaperAgentRolePolicy.commonCapability("direct-postgresql-writer"));
        assertEquals(
            "paper-agent-is-installed-on-lobby-and-island-nodes-role-controls-runtime-behavior",
            PaperAgentRolePolicy.INSTALLATION_POLICY
        );
    }

    @Test
    void pinsLobbyRoleAsGuiAndQueryOnly() {
        assertEquals(
            List.of(
                "island-gui",
                "island-create-menu",
                "island-ranking",
                "invite-accept-decline",
                "island-settings-gui",
                "visit-gui",
                "admin-query-gui"
            ),
            PaperAgentRolePolicy.lobbyCapabilities()
        );
        assertTrue(PaperAgentRolePolicy.lobbyCapability("island-create-menu"));
        assertTrue(PaperAgentRolePolicy.lobbyCapability(" VISIT-GUI "));
        assertFalse(PaperAgentRolePolicy.lobbyCapability("island-save"));
        assertEquals(
            "lobby-role-never-activates-runs-saves-or-restores-island-worlds",
            PaperAgentRolePolicy.LOBBY_WORLD_EXECUTION_POLICY
        );
    }

    @Test
    void pinsIslandNodeRuntimeResponsibilities() {
        assertEquals(
            List.of(
                "island-activation",
                "island-deactivation",
                "island-save",
                "island-snapshot-create",
                "shard-world-management",
                "cell-allocate-release",
                "chunk-preload",
                "protection-event-handling",
                "permission-cache-maintenance",
                "island-teleport",
                "visitor-spawn",
                "member-join-quit",
                "active-island-heartbeat"
            ),
            PaperAgentRolePolicy.islandNodeCapabilities()
        );
        assertTrue(PaperAgentRolePolicy.islandNodeCapability("island-activation"));
        assertTrue(PaperAgentRolePolicy.islandNodeCapability(" CHUNK-PRELOAD "));
        assertTrue(PaperAgentRolePolicy.islandNodeCapability("active-island-heartbeat"));
        assertFalse(PaperAgentRolePolicy.islandNodeCapability("admin-query-gui"));
        assertEquals(
            "island-node-role-owns-active-world-runtime-protection-teleport-save-snapshot-and-heartbeat",
            PaperAgentRolePolicy.ISLAND_NODE_EXECUTION_POLICY
        );
    }

    @Test
    void keepsPaperAgentBehindCoreApiWriteBoundary() {
        assertEquals(
            "paper-agent-never-writes-core-database-directly-uses-core-api-client",
            PaperAgentRolePolicy.DIRECT_WRITE_POLICY
        );
        assertTrue(PaperAgentRolePolicy.roleSummary("LOBBY").contains("island-ranking"));
        assertTrue(PaperAgentRolePolicy.roleSummary("ISLAND_NODE").contains("shard-world-management"));
        assertEquals("", PaperAgentRolePolicy.roleSummary("UNKNOWN"));
    }
}
