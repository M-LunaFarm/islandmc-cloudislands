package kr.seungmin.satisskyfactory.node;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.model.ResourceNodeType;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import kr.seungmin.satisskyfactory.task.DirtySaveService;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class ResourceNodeService {
    private final DatabaseService database;
    private final ConcurrentHashMap<UUID, List<ResourceNode>> nodesByIsland = new ConcurrentHashMap<>();
    private NodeGenerationService nodeGeneration;
    private DirtySaveService dirtySaves;
    private BooleanSupplier writesEnabled = () -> true;
    private boolean enabled;
    private boolean regenerationEnabled = true;
    private long minimumRegenerationIntervalMillis = 1000L;

    public ResourceNodeService(DatabaseService database) {
        this.database = database;
    }

    public void load(FileConfiguration config) {
        enabled = true;
        this.nodeGeneration = new NodeGenerationService(config);
        regenerationEnabled = config.getBoolean("resource-nodes.regeneration.enabled",
                config.getBoolean("regeneration.enabled", true));
        minimumRegenerationIntervalMillis = Math.max(0L, config.getLong("resource-nodes.regeneration.minimum-interval-ms",
                config.getLong("regeneration.minimum-interval-ms", 1000L)));
    }

    public void clear() {
        enabled = false;
        nodesByIsland.clear();
        nodeGeneration = null;
        regenerationEnabled = false;
        minimumRegenerationIntervalMillis = 1000L;
    }

    public List<ResourceNode> nodes(UUID islandUuid) {
        if (!enabled) {
            return List.of();
        }
        List<ResourceNode> nodes = nodesByIsland.computeIfAbsent(islandUuid, database::loadNodes);
        List<ResourceNode> regenerated = nodes.stream().map(this::regenerate).toList();
        if (regenerated != nodes) {
            nodesByIsland.put(islandUuid, regenerated);
        }
        return List.copyOf(regenerated);
    }

    public List<ResourceNode> generateIfMissing(UUID islandUuid, Location origin) {
        return generateIfMissing(islandUuid, origin, location -> true);
    }

    public List<ResourceNode> generateIfMissing(UUID islandUuid, Location origin, Predicate<Location> insideIsland) {
        if (!enabled) {
            return List.of();
        }
        List<ResourceNode> existing = nodes(islandUuid);
        if (!existing.isEmpty()) {
            return existing;
        }
        if (nodeGeneration == null) {
            return existing;
        }
        BlockKey originKey = BlockKey.from(origin);
        for (ResourceNode node : nodeGeneration.generate(islandUuid, originKey,
                location -> insideIsland.test(new Location(origin.getWorld(), location.x(), location.y(), location.z())),
                System.currentTimeMillis())) {
            save(node);
        }
        return nodes(islandUuid);
    }

    public Optional<ResourceNode> nearest(UUID islandUuid, BlockKey location, int maxDistance) {
        return nearest(islandUuid, location, maxDistance, null);
    }

    public Optional<ResourceNode> nearest(UUID islandUuid, BlockKey location, int maxDistance, ResourceNodeType type) {
        return nodes(islandUuid).stream()
                .filter(node -> node.location().world().equals(location.world()))
                .filter(node -> type == null || type.matches(node.nodeType()))
                .filter(node -> distanceSquared(node.location(), location) <= maxDistance * maxDistance)
                .min(Comparator.comparingInt(node -> distanceSquared(node.location(), location)));
    }

    public boolean save(ResourceNode node) {
        if (!enabled) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        long previousUpdatedAt = node.updatedAt();
        node.updatedAt(System.currentTimeMillis());
        if (dirtySaves != null) {
            if (!dirtySaves.markNode(node)) {
                node.updatedAt(previousUpdatedAt);
                return false;
            }
        } else {
            try {
                database.saveNode(node);
            } catch (RuntimeException exception) {
                node.updatedAt(previousUpdatedAt);
                return false;
            }
        }
        cache(node);
        return true;
    }

    public void dirtySaves(DirtySaveService dirtySaves) {
        this.dirtySaves = dirtySaves;
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.writesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    public boolean remapIslandRegion(UUID islandUuid, String worldName, int deltaX, int deltaY, int deltaZ) {
        if (!enabled) {
            return false;
        }
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        List<ResourceNode> current = nodes(islandUuid);
        List<ResourceNode> updated = new ArrayList<>();
        for (ResourceNode node : current) {
            if (worldName.equals(node.location().world()) && deltaX == 0 && deltaY == 0 && deltaZ == 0) {
                updated.add(node);
                continue;
            }
            try {
                updated.add(new ResourceNode(
                        node.nodeId(),
                        node.islandUuid(),
                        node.nodeType(),
                        node.resourceId(),
                        node.purity(),
                        node.remaining(),
                        node.maxRemaining(),
                        node.regenPerHour(),
                        node.requiredMachineTier(),
                        new BlockKey(worldName,
                                Math.addExact(node.location().x(), deltaX),
                                Math.addExact(node.location().y(), deltaY),
                                Math.addExact(node.location().z(), deltaZ)),
                        node.createdAt(),
                        node.updatedAt()
                ));
            } catch (ArithmeticException overflow) {
                return false;
            }
        }
        boolean changed = !updated.equals(current);
        if (!changed) {
            return false;
        }
        long now = System.currentTimeMillis();
        updated.forEach(node -> node.updatedAt(now));
        if (dirtySaves != null) {
            if (!dirtySaves.markNodes(updated)) {
                return false;
            }
        } else {
            try {
                database.saveNodes(updated);
            } catch (RuntimeException exception) {
                return false;
            }
        }
        nodesByIsland.put(islandUuid, updated);
        return true;
    }

    public void forgetIsland(UUID islandUuid) {
        List<ResourceNode> removed = nodesByIsland.remove(islandUuid);
        if (removed == null || dirtySaves == null) {
            return;
        }
        dirtySaves.forgetIsland(islandUuid);
    }

    private void cache(ResourceNode node) {
        nodesByIsland.compute(node.islandUuid(), (islandUuid, current) -> {
            List<ResourceNode> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
            updated.removeIf(existing -> existing.nodeId().equals(node.nodeId()));
            updated.add(node);
            return updated;
        });
    }

    private ResourceNode regenerate(ResourceNode node) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - node.updatedAt());
        if (!regenerationEnabled || node.remaining() >= node.maxRemaining() || node.regenPerHour() <= 0
                || elapsed < minimumRegenerationIntervalMillis) {
            return node;
        }
        long restored = Math.floorDiv(node.regenPerHour() * elapsed, 60L * 60L * 1000L);
        if (restored <= 0) {
            return node;
        }
        long before = node.remaining();
        node.remaining(Math.min(node.maxRemaining(), before + restored));
        if (node.remaining() != before) {
            if (!save(node)) {
                node.remaining(before);
            }
        }
        return node;
    }

    private int distanceSquared(BlockKey a, BlockKey b) {
        int dx = a.x() - b.x();
        int dy = a.y() - b.y();
        int dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean writesEnabled() {
        try {
            return writesEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

}
