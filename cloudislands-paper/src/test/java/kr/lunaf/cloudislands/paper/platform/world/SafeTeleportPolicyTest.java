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
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String korean = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/ko_kr.yml"));
        String english = Files.readString(Path.of("src/main/resources/config-v2/ui/messages/en_us.yml"));

        assertTrue(resolver.contains("HORIZONTAL_RADIUS = 4"));
        assertTrue(resolver.contains("VERTICAL_RADIUS = 8"));
        assertTrue(resolver.contains("boundary.contains(world.getName(), x, z)"));
        assertTrue(resolver.contains("block.isPassable()") && resolver.contains("!block.isLiquid()"));
        assertTrue(resolver.contains("Material.LAVA") && resolver.contains("Material.POWDER_SNOW") && resolver.contains("Material.MAGMA_BLOCK"));
        assertTrue(gateway.contains("getChunkAtAsync") && gateway.contains("SafeTeleportResolver.resolve"));
        assertTrue(local.contains("protection.region(islandId)"));
        assertTrue(local.contains("worlds.safeDestination(requested, region.get())"));
        assertTrue(tickets.contains("worlds.safeDestination(requested, targetRegion(ticket.islandId()))"));
        assertTrue(tickets.contains("UNSAFE_TELEPORT_TARGET"));
        assertTrue(boundary.contains("pendingReturns.add(playerUuid)"));
        assertTrue(boundary.contains("worlds.safeDestination(target, region)"));
        assertTrue(boundary.contains("boundary-return-unsafe"));
        assertTrue(bootstrap.contains("new IslandBoundaryListener(plugin, plugin.agent.protection(), plugin.messages)"));
        assertTrue(korean.contains("boundary-return-unsafe:") && english.contains("boundary-return-unsafe:"));
    }
}
