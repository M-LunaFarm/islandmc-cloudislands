package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SafeTeleportPolicyTest {
    @Test
    void localAndTicketTeleportsLoadChunksAndResolveBoundedSafeLocations() throws Exception {
        String resolver = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/SafeTeleportResolver.java"));
        String gateway = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/BukkitWorldGateway.java"));
        String local = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandLocalTeleports.java"));
        String tickets = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/RouteTicketConsumer.java"));
        String boundary = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandBoundaryListener.java"));
        String portals = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandPortalListener.java"));
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String korean = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/ko_kr.yml"));
        String english = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/en_us.yml"));

        assertTrue(resolver.contains("HORIZONTAL_RADIUS = 4"));
        assertTrue(resolver.contains("VERTICAL_RADIUS = 8"));
        assertTrue(resolver.contains("boundary.contains(world.getName(), x, z)"));
        assertTrue(resolver.contains("Double.isFinite(location.getX())"));
        assertTrue(resolver.contains("x + 0.5D") && resolver.contains("z + 0.5D"));
        assertTrue(resolver.contains("public static boolean isSafe(Location destination, IslandRegion boundary)"));
        assertTrue(resolver.contains("block.isPassable()") && resolver.contains("!block.isLiquid()"));
        assertTrue(resolver.contains("Material.LAVA") && resolver.contains("Material.POWDER_SNOW") && resolver.contains("Material.MAGMA_BLOCK"));
        assertTrue(gateway.contains("getChunkAtAsync") && gateway.contains("SafeTeleportResolver.resolve"));
        assertTrue(gateway.contains("PaperSchedulers.run(plugin"));
        assertTrue(local.contains("protection.region(islandId)"));
        assertTrue(local.contains("worlds.safeDestination(requested, region.get())"));
        assertTrue(local.contains("SafeTeleportResolver.isSafe(destination.get(), region.get())"));
        assertTrue(tickets.contains("worlds.safeDestination(requested, targetRegion)"));
        assertTrue(tickets.contains("UNSAFE_TELEPORT_TARGET"));
        assertTrue(tickets.contains("TELEPORT_TARGET_CHANGED"));
        assertTrue(tickets.contains("islandTransitioning(ticket.islandId())"));
        assertTrue(tickets.contains("ISLAND_TRANSITION_IN_PROGRESS"));
        assertTrue(tickets.contains("worlds.safeDestination(target, null)"));
        assertTrue(boundary.contains("pendingReturns.add(playerUuid)"));
        assertTrue(boundary.contains("BoundaryReturnRequest.capture(player, from)"));
        assertTrue(boundary.contains("players.onlinePlayer(playerUuid)"));
        assertTrue(boundary.contains("request.isCurrent(activePlayer)"));
        assertTrue(boundary.contains("worlds.safeDestination(target, region)"));
        assertTrue(boundary.contains("SafeTeleportResolver.isSafe(destination.get(), region)"));
        assertTrue(boundary.contains("boundary-return-unsafe"));
        assertTrue(bootstrap.contains("new IslandBoundaryListener(plugin, plugin.agent.protection(), plugin.messages)"));
        assertTrue(bootstrap.contains("new IslandPortalListener(plugin.agent.protection(), plugin.messages)"));
        assertTrue(portals.contains("single-world-island-regions-deny-cross-dimension-portals"));
        assertTrue(portals.contains("PlayerPortalEvent") && portals.contains("EntityPortalEvent"));
        assertTrue(korean.contains("boundary-return-unsafe:") && english.contains("boundary-return-unsafe:"));
        assertTrue(korean.contains("portal-cross-dimension-denied:") && english.contains("portal-cross-dimension-denied:"));
    }
}
