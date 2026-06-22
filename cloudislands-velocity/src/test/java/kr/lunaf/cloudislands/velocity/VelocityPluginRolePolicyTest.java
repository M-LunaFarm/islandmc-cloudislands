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
        assertFalse(source.contains("sendBodyResult("), "Velocity actions must not name message results as raw response bodies");
        assertFalse(source.contains("bodyResultMessage("), "Velocity actions must not route typed client results through raw body formatters");
        assertFalse(source.contains("CompletableFuture<String>"), "Velocity presentation helpers must not expose raw String futures");
        assertFalse(source.contains("VelocityPlayerPayloadFormatter"), "Velocity actions must not parse raw player payloads");
        assertFalse(source.contains("sendInviteActionResult("), "Velocity actions must not infer invite success from raw JSON bodies");
        assertFalse(source.contains("body.contains(\"\\\"accepted\\\":false\")"), "Velocity actions must not inspect raw JSON success flags");
        assertFalse(source.contains("routeDebugMessage(String body)"), "Velocity route debug actions must use typed route debug views");
        assertFalse(source.contains("routeTicketMessage(String body)"), "Velocity route ticket actions must use typed route ticket views");
        assertFalse(source.contains("routeClearMessage(String body)"), "Velocity route clear actions must use typed route clear views");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityRoutingActions.java")).contains("routeClearMessage(String body)"), "Velocity routing facade must not retain raw route clear forwarding");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityRoutingController.java")).contains("routeClearMessage(String body)"), "Velocity routing controller must not expose raw route clear formatting helpers");
        String routeFormatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityRouteMessageFormatter.java"));
        assertFalse(routeFormatter.contains("debug(String body)"), "Velocity route debug formatter must not parse raw Core route JSON");
        assertFalse(routeFormatter.contains("ticket(String body)"), "Velocity route ticket formatter must not parse raw Core route JSON");
        assertFalse(routeFormatter.contains("clear(String body)"), "Velocity route clear formatter must not parse raw Core route JSON");
        assertFalse(routeFormatter.contains("ticketSummary(String object)"), "Velocity route ticket summaries must use typed ticket views");
        assertFalse(source.contains("snapshotListMessage(String body)"), "Velocity snapshot actions must use typed snapshot views");
        assertFalse(Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocitySnapshotMessageFormatter.java")).contains("snapshotList(String body)"), "Velocity snapshot formatter must not parse raw Core snapshot JSON");

        String formatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityIslandMessageFormatter.java"));
        assertFalse(formatter.contains("playerIslands(String body)"), "Velocity player island formatter must use typed island views");
        assertFalse(formatter.contains("publicIslands(String body)"), "Velocity public island formatter must use typed island views");
        assertFalse(formatter.contains("invites(String body)"), "Velocity invite formatter must use typed invite views");
        assertFalse(formatter.contains("inviteCreate(String body)"), "Velocity invite creation formatter must use typed invite views");
        assertFalse(formatter.contains("chatResult(String label, String body)"), "Velocity chat formatter must use typed chat action views");
        assertFalse(formatter.contains("islandInfo(String body)"), "Velocity island info formatter must use typed island info views");
        assertFalse(formatter.contains("islandStat(String label, String field, String body)"), "Velocity island stat formatter must use typed island info views");
        assertFalse(formatter.contains("biomeInfo(String body)"), "Velocity biome formatter must use typed biome views");
        assertFalse(formatter.contains("runtimeInfo(String body)"), "Velocity runtime formatter must use typed runtime views");
        assertFalse(formatter.contains("playerInfo(String body)"), "Velocity player formatter must use typed player views");
        assertFalse(formatter.contains("rankingList(String label, String body)"), "Velocity ranking formatter must use typed ranking views");
        assertFalse(formatter.contains("blockValueList(String body)"), "Velocity block value formatter must use typed block value views");
        assertFalse(formatter.contains("addonStateSummary(String body)"), "Velocity addon state formatter must use typed addon state views");
        assertFalse(formatter.contains("templateList(String body)"), "Velocity template formatter must use typed template views");
        assertFalse(formatter.contains("warpList(String label, String body)"), "Velocity warp formatter must use typed warp views");
        assertFalse(formatter.contains("homeList(String body)"), "Velocity home formatter must use typed home views");
        assertFalse(formatter.contains("memberList(String body)"), "Velocity member formatter must use typed member views");
        assertFalse(formatter.contains("banList(String body)"), "Velocity ban formatter must use typed ban views");
        assertFalse(formatter.contains("permissionList(String body)"), "Velocity permission formatter must use typed permission views");
        assertFalse(formatter.contains("roleList(String body)"), "Velocity role formatter must use typed role views");
        assertFalse(formatter.contains("islandLogList(String body)"), "Velocity log formatter must use typed log views");
        assertFalse(formatter.contains("bankInfo(String body)"), "Velocity bank formatter must use typed bank views");
        assertFalse(formatter.contains("levelRecalculation(String body)"), "Velocity level formatter must use typed level views");
        assertFalse(formatter.contains("upgradeList(String body)"), "Velocity upgrade formatter must use typed upgrade views");
        assertFalse(formatter.contains("generatorInfo(String body)"), "Velocity generator formatter must use typed upgrade views");
        assertFalse(formatter.contains("upgradePurchase(String body)"), "Velocity upgrade purchase formatter must use typed mutation views");
        assertFalse(formatter.contains("missionList(String label, String body)"), "Velocity mission formatter must use typed mission views");
        assertFalse(formatter.contains("missionResult(String label, String body)"), "Velocity mission result formatter must use typed mutation views");
        assertFalse(formatter.contains("limitList(String body)"), "Velocity limit formatter must use typed limit views");
        assertFalse(formatter.contains("limitResult(String body)"), "Velocity limit mutation formatter must use typed mutation views");
        assertFalse(formatter.contains("flagList(String body)"), "Velocity flag formatter must use typed flag views");
        assertFalse(formatter.contains("upgradeRules(String body)"), "Velocity upgrade rules formatter must use typed rule views");
        assertFalse(formatter.contains("actionResult(String label, String targetId, String body)"), "Velocity lifecycle and block actions must use typed action views");
        assertFalse(formatter.contains("body.contains(\"\\\"accepted\\\""), "Velocity message formatter must inspect accepted through parsed JSON fields");
        assertFalse(formatter.contains("body.contains(\"\\\"snapshotNo\\\""), "Velocity message formatter must inspect snapshotNo through parsed JSON fields");

        String coreStatusFormatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityCoreStatusMessageFormatter.java"));
        assertFalse(coreStatusFormatter.contains("maintenance(String label, String body)"), "Velocity maintenance formatter must use typed maintenance views");
        assertFalse(coreStatusFormatter.contains("metrics(String body)"), "Velocity metrics formatter must use typed metrics views");
        assertFalse(coreStatusFormatter.contains("addonEndpoints(String body)"), "Velocity addon endpoint formatter must use typed config views");

        String eventFormatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityEventMessageFormatter.java"));
        assertFalse(eventFormatter.contains("events(String body)"), "Velocity event formatter must use typed event stream views");
        assertFalse(eventFormatter.contains("audit(String body)"), "Velocity audit formatter must use typed audit views");

        String nodeJobFormatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityNodeJobMessageFormatter.java"));
        assertFalse(nodeJobFormatter.contains("appendLevelScanSummary(String body)"), "Velocity node formatter must use typed node views");
        assertFalse(nodeJobFormatter.contains("nodeIslandList(String body)"), "Velocity node island formatter must use typed runtime views");
        assertFalse(nodeJobFormatter.contains("storageStatus(String body)"), "Velocity storage formatter must use typed storage views");
        assertFalse(nodeJobFormatter.contains("nodeListSummary(String body)"), "Velocity node list formatter must use typed node snapshots");
        assertFalse(nodeJobFormatter.contains("nodeActionSummary(String label, String nodeId, String body)"), "Velocity node action formatter must use typed action views");
        assertFalse(nodeJobFormatter.contains("nodeSweep(String body)"), "Velocity node sweep formatter must use typed action views");
        assertFalse(nodeJobFormatter.contains("jobList(String body)"), "Velocity job formatter must use typed job views");
        assertFalse(nodeJobFormatter.contains("jobAction(String action, String body)"), "Velocity job action formatter must use typed job action views");

        String coreConfigFormatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityCoreConfigMessageFormatter.java"));
        assertFalse(coreConfigFormatter.contains("format(String body)"), "Velocity core config formatter must use typed config views");
        assertFalse(Files.exists(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityJsonFields.java")), "Velocity presentation must not keep a raw JSON field helper");
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
