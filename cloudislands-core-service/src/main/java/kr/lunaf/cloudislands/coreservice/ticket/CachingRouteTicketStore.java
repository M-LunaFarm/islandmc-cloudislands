package kr.lunaf.cloudislands.coreservice.ticket;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingRouteTicketStore implements RouteTicketStore {
    private final RouteTicketStore delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingRouteTicketStore(RouteTicketStore delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public RouteTicket save(RouteTicket ticket) {
        return cache(delegate.save(ticket));
    }

    @Override
    public int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Instant expiresAt, Map<String, String> payload) {
        int updated = delegate.markReadyForIsland(islandId, targetNode, targetWorld, expiresAt, payload);
        if (updated > 0) {
            cacheTicketsJson();
        }
        return updated;
    }

    @Override
    public List<RouteTicket> markFailedForIsland(UUID islandId, String targetNode, String reason) {
        List<RouteTicket> failed = delegate.markFailedForIsland(islandId, targetNode, reason);
        failed.forEach(this::cache);
        return failed;
    }

    @Override
    public Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        Optional<RouteTicket> ticket = delegate.consume(ticketId, playerUuid, nodeId, nonce);
        ticket.ifPresent(this::cache);
        return ticket;
    }

    @Override
    public Optional<RouteTicket> find(UUID ticketId) {
        Optional<RouteTicket> ticket = delegate.find(ticketId);
        ticket.ifPresent(this::cache);
        return ticket;
    }

    @Override
    public Optional<RouteTicket> findLatestForPlayer(UUID playerUuid) {
        Optional<RouteTicket> cached = cachedPlayerTicket(playerUuid);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<RouteTicket> ticket = delegate.findLatestForPlayer(playerUuid);
        ticket.ifPresent(this::cache);
        return ticket;
    }

    @Override
    public Map<String, Long> countsByState() {
        return delegate.countsByState();
    }

    @Override
    public List<RouteTicket> expireStale() {
        List<RouteTicket> expired = delegate.expireStale();
        expired.forEach(this::cache);
        return expired;
    }

    @Override
    public boolean clear(UUID ticketId) {
        Optional<RouteTicket> ticket = delegate.find(ticketId);
        boolean cleared = delegate.clear(ticketId);
        if (cleared) {
            ticket.map(RouteTicket::playerUuid).ifPresent(this::deletePlayerTicket);
        }
        return cleared;
    }

    @Override
    public int clearAll() {
        return delegate.clearAll();
    }

    @Override
    public String toJson() {
        return delegate.toJson();
    }

    public long failuresTotal() {
        return failures.get();
    }

    private RouteTicket cache(RouteTicket ticket) {
        long ttlMillis = Math.max(1_000L, ticket.expiresAt().toEpochMilli() - Instant.now().toEpochMilli());
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.playerRouteTicket(ticket.playerUuid()), ticketJson(ticket), "PX", Long.toString(ttlMillis));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return ticket;
    }

    private void deletePlayerTicket(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("DEL", RedisKeys.playerRouteTicket(playerUuid));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void cacheTicketsJson() {
        String json = delegate.toJson();
        int index = 0;
        while (true) {
            int playerField = json.indexOf("\"playerUuid\":\"", index);
            if (playerField < 0) {
                return;
            }
            int playerStart = playerField + "\"playerUuid\":\"".length();
            int playerEnd = json.indexOf('"', playerStart);
            int objectStart = json.lastIndexOf('{', playerField);
            int objectEnd = matchingObjectEnd(json, objectStart);
            if (playerEnd < 0 || objectStart < 0 || objectEnd < 0) {
                return;
            }
            try {
                String ticketJson = json.substring(objectStart, objectEnd + 1);
                cache(ticketFromJson(ticketJson));
            } catch (RuntimeException ignored) {
                failures.incrementAndGet();
            }
            index = objectEnd + 1;
        }
    }

    private Optional<RouteTicket> cachedPlayerTicket(UUID playerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.playerRouteTicket(playerUuid));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            RouteTicket ticket = ticketFromJson(json);
            if (ticket.expiresAt().isBefore(Instant.now())) {
                deletePlayerTicket(playerUuid);
                return Optional.empty();
            }
            return Optional.of(ticket);
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static RouteTicket ticketFromJson(String json) {
        return new RouteTicket(
            JsonFields.uuid(json, "ticketId", new UUID(0L, 0L)),
            JsonFields.uuid(json, "playerUuid", new UUID(0L, 0L)),
            JsonFields.enumValue(RouteAction.class, json, "action", RouteAction.HOME),
            JsonFields.uuid(json, "islandId", new UUID(0L, 0L)),
            JsonFields.text(json, "targetNode", ""),
            JsonFields.text(json, "targetWorld", ""),
            JsonFields.enumValue(RouteTicketState.class, json, "state", RouteTicketState.FAILED),
            parseInstant(JsonFields.text(json, "expiresAt", "")),
            JsonFields.text(json, "nonce", ""),
            JsonFields.object(json, "payload")
        );
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static int matchingObjectEnd(String json, int objectStart) {
        if (objectStart < 0) {
            return -1;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String ticketJson(RouteTicket ticket) {
        return new StringBuilder("{")
            .append("\"ticketId\":\"").append(ticket.ticketId()).append("\",")
            .append("\"playerUuid\":\"").append(ticket.playerUuid()).append("\",")
            .append("\"action\":\"").append(ticket.action().name()).append("\",")
            .append("\"islandId\":\"").append(ticket.islandId()).append("\",")
            .append("\"targetNode\":\"").append(escape(ticket.targetNode())).append("\",")
            .append("\"targetWorld\":\"").append(escape(ticket.targetWorld())).append("\",")
            .append("\"targetServerName\":\"").append(escape(ticket.payload().getOrDefault("targetServerName", ticket.targetNode()))).append("\",")
            .append("\"state\":\"").append(ticket.state().name()).append("\",")
            .append("\"expiresAt\":\"").append(ticket.expiresAt()).append("\",")
            .append("\"nonce\":\"").append(escape(ticket.nonce())).append("\",")
            .append("\"payload\":").append(payloadJson(ticket.payload()))
            .append('}')
            .toString();
    }

    private static String payloadJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
