package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public record CoreCacheClearEvent(String scope, int sessions, int tickets, int redisKeys, Instant occurredAt) implements CloudGlobalEvent {}
