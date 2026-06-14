package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record AddonStateChangeEvent(String addonId, UUID islandId, String operation, String key, String table, int keys, int tables, Instant occurredAt) implements CloudGlobalEvent {}
