package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandSizeSnapshot(UUID islandId, int size, int border) {}
