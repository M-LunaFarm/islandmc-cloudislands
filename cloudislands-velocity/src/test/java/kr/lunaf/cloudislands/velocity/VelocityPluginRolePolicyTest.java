package kr.lunaf.cloudislands.velocity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityPluginRolePolicyTest {
    @Test
    void velocityOwnsTheGlobalPlayerEntryAndRoutingFlow() {
        assertTrue(VelocityPluginRolePolicy.globalCommandAliases().contains("/is"));
        assertTrue(VelocityPluginRolePolicy.globalCommandAliases().contains("/island"));
        assertTrue(VelocityPluginRolePolicy.globalCommandAliases().contains("/\uC12C"));

        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("global-is-command"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("global-korean-island-command"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("server-state-reflection"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("route-ticket-create-request"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("backend-connect-request"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("pending-route-on-join"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("server-name-redaction"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("fallback-transfer"));
        assertTrue(VelocityPluginRolePolicy.ownsProxyResponsibility("server-switch-failure-recovery"));
    }

    @Test
    void velocityNeverOwnsIslandRuntimeOrStorageExecution() {
        assertTrue(VelocityPluginRolePolicy.forbidsRuntimeResponsibility("paper-world-execution"));
        assertTrue(VelocityPluginRolePolicy.forbidsRuntimeResponsibility("island-region-save"));
        assertTrue(VelocityPluginRolePolicy.forbidsRuntimeResponsibility("snapshot-bundle-write"));
        assertTrue(VelocityPluginRolePolicy.forbidsRuntimeResponsibility("protection-event-decision"));
        assertTrue(VelocityPluginRolePolicy.forbidsRuntimeResponsibility("satis-runtime-tick"));
        assertTrue(VelocityPluginRolePolicy.forbidsRuntimeResponsibility("direct-island-database-write"));

        Set<String> owned = new HashSet<>();
        owned.addAll(VelocityPluginRolePolicy.entryResponsibilities());
        owned.addAll(VelocityPluginRolePolicy.routingResponsibilities());
        owned.addAll(VelocityPluginRolePolicy.failureResponsibilities());

        for (String forbidden : VelocityPluginRolePolicy.forbiddenRuntimeResponsibilities()) {
            assertFalse(owned.contains(forbidden));
            assertFalse(VelocityPluginRolePolicy.ownsProxyResponsibility(forbidden));
        }
    }

    @Test
    void velocityActionSupportDoesNotKeepRawJsonActionHelpers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityActionSupport.java"));

        assertFalse(source.contains("sendPlayerPayloadFuture("), "Velocity actions must not keep raw player payload futures");
        assertFalse(source.contains("sendInviteActionResult("), "Velocity actions must not infer invite success from raw JSON bodies");
        assertFalse(source.contains("body.contains(\"\\\"accepted\\\":false\")"), "Velocity actions must not inspect raw JSON success flags");
    }

    @Test
    void membershipCommandsUseRoleKeysInsteadOfIslandRoleOverloads() throws Exception {
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerMembershipActions.java"));
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));
        String support = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSupport.java"));

        assertFalse(actions.contains("IslandRole"), "Membership actions must expose role-key commands, not IslandRole overloads");
        assertFalse(dispatcher.contains("IslandRole"), "Membership commands must normalize role keys without the legacy enum");
        assertFalse(support.contains("parseRole("), "Shared command support must not keep legacy IslandRole parsing");
        assertFalse(support.contains("memberRoleNames("), "Role completions must come from dynamic role keys");
    }
}
