package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public record CoreReloadEvent(int clearedSessions, int clearedTickets, int clearedRedisKeys, Instant occurredAt) implements CloudGlobalEvent {}
