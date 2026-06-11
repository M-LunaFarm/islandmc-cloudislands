package kr.lunaf.cloudislands.protocol.route;

import java.util.UUID;

public record CreateIslandRequest(UUID playerUuid, String templateId, String requestId) {}
