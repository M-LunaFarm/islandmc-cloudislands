package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandUpgradeEvent(UUID islandId, String upgradeKey, int level, Instant occurredAt) implements CloudIslandEvent {}
