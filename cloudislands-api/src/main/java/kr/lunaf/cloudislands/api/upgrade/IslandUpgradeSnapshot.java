package kr.lunaf.cloudislands.api.upgrade;

import java.time.Instant;
import java.util.UUID;

public record IslandUpgradeSnapshot(UUID islandId, String upgradeKey, UpgradeType type, int level, Instant updatedAt) {}
