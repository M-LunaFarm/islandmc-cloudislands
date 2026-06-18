package kr.lunaf.cloudislands.velocity;

import org.junit.jupiter.api.Test;

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
}
