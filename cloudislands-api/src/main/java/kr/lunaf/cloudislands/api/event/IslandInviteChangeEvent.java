package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public record IslandInviteChangeEvent(UUID islandId, UUID inviteId, UUID playerUuid, UUID targetUuid, String state, Boolean accepted, Boolean declined, Instant occurredAt) implements CloudIslandEvent {}
