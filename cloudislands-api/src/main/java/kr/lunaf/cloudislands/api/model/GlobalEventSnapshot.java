package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record GlobalEventSnapshot(String type, Map<String, String> fields, Instant occurredAt) {}
