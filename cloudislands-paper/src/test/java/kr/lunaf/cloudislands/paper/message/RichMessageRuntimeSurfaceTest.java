package kr.lunaf.cloudislands.paper.message;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RichMessageRuntimeSurfaceTest {
    @Test
    void routeAndMigrationBossBarsUseConfiguredComponents() throws IOException {
        String routeConsumer = source("RouteTicketConsumer.java");
        String migrationPoller = source("cache/PermissionEventPoller.java");
        String routingCommands = source("command/IslandRoutingCommandHandler.java");

        assertTrue(routeConsumer.contains("BossBar.bossBar(loading"));
        assertTrue(routeConsumer.contains("bar.name(loading)"));
        assertFalse(routeConsumer.contains("BossBar.bossBar(Component.text(playerMessage"));
        assertTrue(migrationPoller.contains("BossBar.bossBar(primary.append(Component.space()).append(secondary)"));
        assertFalse(migrationPoller.contains("BossBar.bossBar(Component.text(primary"));
        assertTrue(routingCommands.contains("BossBar.bossBar(runtime.component(player, title)"));
        assertTrue(routingCommands.contains("bossBar.name(runtime.component(player, title))"));
    }

    @Test
    void actionBarsTitlesKicksAndCommandMessagesUseConfiguredComponents() throws IOException {
        String routeConsumer = source("RouteTicketConsumer.java");
        String routeSession = source("session/PaperRouteSessionListener.java");
        String boundary = source("IslandBoundaryListener.java");
        String flags = source("IslandGameplayFlagListener.java");
        String router = source("command/IslandCommandRouter.java");
        String messenger = source("command/IslandCommandMessenger.java");
        String protection = source("IslandProtectionListener.java");
        String admin = source("admin/AdminCommandBackend.java");

        assertFalse(routeConsumer.contains("sendActionBar(Component.text(playerMessage"));
        assertFalse(routeSession.contains("Component.text(playerMessage"));
        assertTrue(routeSession.contains("playerComponent(\"route-login-session-required\""));
        assertTrue(boundary.contains("sendActionBar(component(player"));
        assertTrue(flags.contains("sendActionBar(component("));
        assertTrue(router.contains("player.showTitle(Title.title(\n            runtime.component(player, title)"));
        assertTrue(messenger.contains("Player activePlayer = plugin.getServer().getPlayer(player.getUniqueId())"));
        assertTrue(messenger.contains("activePlayer.sendMessage(component(activePlayer, message))"));
        assertTrue(messenger.indexOf("PaperSchedulers.run(plugin") < messenger.indexOf("player.getUniqueId()"),
            "command responses must resolve the current Player only inside the global scheduler callback");
        assertTrue(protection.contains("messages.componentTextForLocale(PlayerLocaleCache.clientLocale(player), message)"));
        assertTrue(admin.contains("messages.componentText(payload.title())"));
        assertTrue(admin.contains("messages.componentText(message)"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper").resolve(relative));
    }
}
