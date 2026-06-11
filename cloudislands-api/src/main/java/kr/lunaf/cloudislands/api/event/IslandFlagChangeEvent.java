package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public record IslandFlagChangeEvent(UUID islandId, IslandFlag flag, String value, Instant occurredAt) implements CloudIslandEvent {}
