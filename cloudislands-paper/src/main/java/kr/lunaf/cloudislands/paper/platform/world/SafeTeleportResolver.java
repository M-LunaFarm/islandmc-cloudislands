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
        if (requested == null || requested.getWorld() == null) {
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
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1 || !inside(boundary, world, x, z)) {
            return Optional.empty();
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (!standable(floor) || !clear(feet) || !clear(head)) {
            return Optional.empty();
        }
        double targetX = x == requested.getBlockX() ? requested.getX() : x + 0.5D;
        double targetZ = z == requested.getBlockZ() ? requested.getZ() : z + 0.5D;
        return Optional.of(new Location(world, targetX, y, targetZ, requested.getYaw(), requested.getPitch()));
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
