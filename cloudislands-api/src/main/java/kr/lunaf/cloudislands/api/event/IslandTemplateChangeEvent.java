package kr.lunaf.cloudislands.api.event;

import java.time.Instant;

public record IslandTemplateChangeEvent(String templateId, Boolean enabled, String operation, String minNodeVersion, Instant occurredAt) implements CloudGlobalEvent {}
