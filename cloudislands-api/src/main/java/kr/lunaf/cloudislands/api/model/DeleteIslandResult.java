package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record DeleteIslandResult(boolean accepted, String code, UUID islandId) {}
