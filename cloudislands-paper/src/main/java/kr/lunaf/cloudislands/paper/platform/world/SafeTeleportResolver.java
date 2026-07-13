package kr.lunaf.cloudislands.paper.platform.world;

import java.util.Optional;
import java.util.Set;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class SafeTeleportResolver {
    public static final int HORIZONTAL_RADIUS = 4;
    public static final int VERTICAL_RADIUS = 8;
    private static final Set<Material> HAZARDS = Set.of(
        Material.CACTUS,
        Material.CAMPFIRE,
        Material.FIRE,
        Material.LAVA,
        Material.MAGMA_BLOCK,
        Material.POINTED_DRIPSTONE,
        Material.POWDER_SNOW,
        Material.SOUL_CAMPFIRE,
        Material.SOUL_FIRE,
        Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE
    );

    private SafeTeleportResolver() {
    }

    public static Optional<Location> resolve(Location requested, IslandRegion boundary) {
        if (!valid(requested)) {
            return Optional.empty();
        }
        World world = requested.getWorld();
        int baseX = requested.getBlockX();
        int baseY = requested.getBlockY();
        int baseZ = requested.getBlockZ();
        for (int radius = 0; radius <= HORIZONTAL_RADIUS; radius++) {
            for (int x = baseX - radius; x <= baseX + radius; x++) {
                for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                    if (radius > 0 && Math.abs(x - baseX) != radius && Math.abs(z - baseZ) != radius) {
                        continue;
                    }
                    for (int offset = 0; offset <= VERTICAL_RADIUS; offset++) {
                        Optional<Location> above = candidate(world, requested, boundary, x, baseY + offset, z);
                        if (above.isPresent()) {
                            return above;
                        }
                        if (offset > 0) {
                            Optional<Location> below = candidate(world, requested, boundary, x, baseY - offset, z);
                            if (below.isPresent()) {
                                return below;
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Location> candidate(World world, Location requested, IslandRegion boundary, int x, int y, int z) {
        if (!safe(world, boundary, x, y, z)) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x + 0.5D, y, z + 0.5D, requested.getYaw(), requested.getPitch()));
    }

    public static boolean isSafe(Location destination, IslandRegion boundary) {
        return valid(destination) && safe(
            destination.getWorld(),
            boundary,
            destination.getBlockX(),
            destination.getBlockY(),
            destination.getBlockZ()
        );
    }

    static boolean withinBuildHeight(int feetY, int minHeight, int maxHeight) {
        return feetY - 1 > minHeight && feetY + 1 < maxHeight;
    }

    private static boolean safe(World world, IslandRegion boundary, int x, int y, int z) {
        if (!withinBuildHeight(y, world.getMinHeight(), world.getMaxHeight()) || !inside(boundary, world, x, z)) {
            return false;
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        return standable(floor) && clear(feet) && clear(head);
    }

    private static boolean valid(Location location) {
        return location != null
            && location.getWorld() != null
            && Double.isFinite(location.getX())
            && Double.isFinite(location.getY())
            && Double.isFinite(location.getZ());
    }

    private static boolean inside(IslandRegion boundary, World world, int x, int z) {
        return boundary == null || boundary.contains(world.getName(), x, z);
    }

    private static boolean standable(Block block) {
        Material type = block.getType();
        return type.isSolid() && !HAZARDS.contains(type);
    }

    private static boolean clear(Block block) {
        Material type = block.getType();
        return block.isPassable() && !type.isSolid() && !HAZARDS.contains(type) && !block.isLiquid();
    }
}
