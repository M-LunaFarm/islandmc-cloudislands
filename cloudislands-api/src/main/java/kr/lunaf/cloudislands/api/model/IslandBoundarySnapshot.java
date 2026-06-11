package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandBoundarySnapshot(UUID islandId, int size, int border) {}
