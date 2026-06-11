package kr.lunaf.cloudislands.api.model;

import java.util.Map;
import java.util.UUID;

public record IslandFlagsSnapshot(UUID islandId, Map<IslandFlag, String> values) {}
