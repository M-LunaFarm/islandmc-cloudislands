package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandBankChangeEvent(UUID islandId, String operation, String amount, String balance, Instant occurredAt) implements CloudIslandEvent {}
