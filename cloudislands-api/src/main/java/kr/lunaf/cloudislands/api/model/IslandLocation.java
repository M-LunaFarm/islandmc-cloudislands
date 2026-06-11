package kr.lunaf.cloudislands.api.model;

public record IslandLocation(
    String worldName,
    double localX,
    double localY,
    double localZ,
    float yaw,
    float pitch
) {}
