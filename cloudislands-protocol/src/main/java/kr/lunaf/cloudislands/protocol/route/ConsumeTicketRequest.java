package kr.lunaf.cloudislands.protocol.route;

import java.util.UUID;

public record ConsumeTicketRequest(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {}
