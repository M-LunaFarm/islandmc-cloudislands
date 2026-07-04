package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class VelocityPlayerRoutingActionsTest {
    @Test
    void normalizesVelocityEffectiveLocaleForCoreProfiles() {
        assertEquals("ko_kr", VelocityPlayerRoutingActions.normalizedLocale(Locale.KOREA));
        assertEquals("en_us", VelocityPlayerRoutingActions.normalizedLocale(Locale.US));
        assertEquals("ko_kr", VelocityPlayerRoutingActions.normalizedLocale(null));
    }

    @Test
    void recordPlayerProfileSendsLocaleToCore() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));

        assertTrue(source.contains("playerProfileCommands().touch(player.getUniqueId(), player.getUsername(), playerLocale(player))"));
        assertTrue(source.contains("player.getEffectiveLocale()"));
    }

    @Test
    void playerRoutingUsesActionSpecificCooldownPermissions() throws Exception {
        String support = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityActionSupport.java"));
        String routing = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));
        String progression = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerProgressionActions.java"));
        String plugin = Files.readString(Path.of("../cloudislands-paper/src/main/resources/plugin.yml"));
        String runtime = routing + "\n" + progression;

        for (String node : java.util.List.of(
            "cloudislands.bypass.cooldown",
            "cloudislands.bypass.warmup",
            "cloudislands.island.home.cooldown",
            "cloudislands.island.visit.cooldown",
            "cloudislands.island.create.cooldown",
            "cloudislands.island.delete.cooldown",
            "cloudislands.island.reset.cooldown",
            "cloudislands.island.snapshot.cooldown",
            "cloudislands.island.restore.cooldown"
        )) {
            assertTrue(support.contains("\"" + node + "\""), node + " must be a runtime permission constant");
            assertTrue(plugin.contains(node + ":"), node + " must be declared in plugin.yml");
        }

        assertTrue(support.contains("player.hasPermission(BYPASS_COOLDOWN)"), "cooldown bypass must be checked before guard rejection");
        assertTrue(support.contains("player.hasPermission(BYPASS_WARMUP)"), "warmup bypass must suppress route warmup presentation");
        assertTrue(support.contains("routeRequestGuard.allow(player.getUniqueId(), action, bypassCooldown)"), "cooldown guard must receive the action key and bypass decision");
        assertTrue(runtime.contains("allowPlayerAction(player, HOME_COOLDOWN"), "home routes must have their own cooldown key");
        assertTrue(runtime.contains("allowPlayerAction(player, VISIT_COOLDOWN"), "visit routes must have their own cooldown key");
        assertTrue(runtime.contains("allowPlayerAction(player, CREATE_COOLDOWN"), "create must have its own cooldown key");
        assertTrue(runtime.contains("allowPlayerAction(player, DELETE_COOLDOWN"), "delete must have its own cooldown key");
        assertTrue(runtime.contains("allowPlayerAction(player, RESET_COOLDOWN"), "reset must have its own cooldown key");
        assertTrue(runtime.contains("allowPlayerAction(player, SNAPSHOT_COOLDOWN"), "snapshot must have its own cooldown key");
        assertTrue(runtime.contains("allowPlayerAction(player, RESTORE_COOLDOWN"), "restore must have its own cooldown key");
    }

    @Test
    void playerRoutingShowsExplicitDistributedRouteStatus() throws Exception {
        String routing = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));
        String presenter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteProgressPresenter.java"));
        String messages = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityMessages.java"));

        assertTrue(presenter.contains("public void status(Player player, String title, String subtitle)"), "route status must support a single title/actionbar presentation boundary");
        assertTrue(presenter.contains("player.showTitle(Title.title("), "route status must render a title for long-running create/home/visit waits");
        assertTrue(presenter.contains("player.sendActionBar(safeSubtitle)"), "route status must also render an actionbar message");

        assertTrue(messages.contains("\"island-create-node-search\""), "create must disclose node search state");
        assertTrue(messages.contains("\"island-create-restoring\""), "create must disclose world restore state");
        assertTrue(messages.contains("\"island-home-preparing\""), "home must disclose island preparation state");
        assertTrue(messages.contains("\"island-visit-preparing\""), "visit must disclose island preparation state");
        assertTrue(messages.contains("\"island-route-moving\""), "route flows must disclose transfer state");

        assertTrue(routing.contains("progressPresenter.status(player, messages.text(\"island-create-node-search\"), messages.text(\"island-create-starting\"))"), "create must show a status before requesting the Core lifecycle ticket");
        assertTrue(routing.contains("progressPresenter.status(player, messages.text(\"island-create-restoring\"), messages.text(\"island-route-moving\"))"), "accepted create must show restore and transfer state before routing");
        assertTrue(routing.contains("progressPresenter.status(player, messages.text(\"island-home-preparing\"), messages.text(\"island-route-moving\"))"), "home must show wait state before requesting a ticket");
        assertTrue(routing.contains("progressPresenter.status(player, messages.text(\"island-visit-preparing\"), messages.text(\"island-route-moving\"))"), "visit/warp flows must show wait state before requesting a ticket");
    }
}
