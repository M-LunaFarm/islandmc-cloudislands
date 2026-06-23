package kr.lunaf.cloudislands.coreservice.ticket;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingRouteTicketStore implements RouteTicketStore {
    private static final long COUNTS_CACHE_TTL_MILLIS = 5_000L;
    private final RouteTicketStore delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingRouteTicketStore(RouteTicketStore delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public RouteTicket save(RouteTicket ticket) {
        RouteTicket saved = cache(delegate.save(ticket));
        invalidateTicketCounts();
        return saved;
    }

    @Override
    public int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Instant expiresAt, Map<String, String> payload) {
        int updated = delegate.markReadyForIsland(islandId, targetNode, targetWorld, expiresAt, payload);
        if (updated > 0) {
            cacheTicketsJson();
            invalidateTicketCounts();
        }
        return updated;
    }

    @Override
    public List<RouteTicket> markFailedForIsland(UUID islandId, String targetNode, String reason) {
        List<RouteTicket> failed = delegate.markFailedForIsland(islandId, targetNode, reason);
        failed.forEach(this::cache);
        if (!failed.isEmpty()) {
            invalidateTicketCounts();
        }
        return failed;
    }

    @Override
    public List<RouteTicket> markFailedForNode(String targetNode, String reason) {
        List<RouteTicket> failed = delegate.markFailedForNode(targetNode, reason);
        failed.forEach(this::cache);
        if (!failed.isEmpty()) {
            invalidateTicketCounts();
        }
        return failed;
    }

    @Override
    public Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        Optional<RouteTicket> ticket = delegate.consume(ticketId, playerUuid, nodeId, nonce);
        ticket.ifPresent(consumed -> {
            cache(consumed);
            invalidateTicketCounts();
        });
        return ticket;
    }

    @Override
    public Optional<RouteTicket> find(UUID ticketId) {
        Optional<RouteTicket> cached = cachedTicket(ticketId);
        if (cached.isPresent()) {
            return cached;
        }
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
        Optional<Map<String, Long>> cached = cachedCountsByState();
        if (cached.isPresent()) {
            return cached.get();
        }
        Map<String, Long> counts = delegate.countsByState();
        cacheCountsByState(counts);
        return counts;
    }

    @Override
    public List<RouteTicket> expireStale() {
        List<RouteTicket> expired = delegate.expireStale();
        expired.forEach(this::deleteCachedTicket);
        if (!expired.isEmpty()) {
            invalidateTicketCounts();
        }
        return expired;
    }

    @Override
    public boolean clear(UUID ticketId) {
        Optional<RouteTicket> ticket = delegate.find(ticketId);
        if (ticket.isEmpty()) {
            ticket = cachedTicket(ticketId);
        }
        boolean cleared = delegate.clear(ticketId);
        ticket.map(RouteTicket::playerUuid).ifPresent(this::deletePlayerTicket);
        deleteTicket(ticketId);
        if (cleared || ticket.isPresent()) {
            invalidateTicketCounts();
        }
        return cleared;
    }

    @Override
    public int clearAll() {
        int cleared = delegate.clearAll();
        clearTicketCaches();
        invalidateTicketCounts();
        return cleared;
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
            String json = RouteTicketJson.storeTicket(ticket);
            redis.command("SET", RedisKeys.playerRouteTicket(ticket.playerUuid()), json, "PX", Long.toString(ttlMillis));
            redis.command("SET", RedisKeys.routeTicket(ticket.ticketId()), json, "PX", Long.toString(ttlMillis));
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

    private void deleteTicket(UUID ticketId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("DEL", RedisKeys.routeTicket(ticketId));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void deleteCachedTicket(RouteTicket ticket) {
        deletePlayerTicket(ticket.playerUuid());
        deleteTicket(ticket.ticketId());
    }

    private void clearTicketCaches() {
        deletePattern("ci:player:*:route-ticket");
        deletePattern("ci:route-ticket:*");
        invalidateTicketCounts();
    }

    private void deletePattern(String pattern) {
        for (String key : keys(pattern)) {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                redis.command("DEL", key);
            } catch (IOException | RuntimeException ignored) {
                failures.incrementAndGet();
            }
        }
    }

    private List<String> keys(String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        do {
            try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
                String response = redis.command("SCAN", cursor, "MATCH", pattern, "COUNT", "100");
                String[] lines = response.split("\\n");
                cursor = lines.length == 0 || lines[0].isBlank() ? "0" : lines[0];
                for (int i = 1; i < lines.length; i++) {
                    if (!lines[i].isBlank()) {
                        keys.add(lines[i]);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                failures.incrementAndGet();
                return keys;
            }
        } while (!"0".equals(cursor));
        return keys;
    }

    private void cacheTicketsJson() {
        String json = delegate.toJson();
        try {
            Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
            for (Object item : SimpleJson.list(root.get("tickets"))) {
                cache(ticketFromObject(SimpleJson.object(item)));
            }
        } catch (RuntimeException ignored) {
            failures.incrementAndGet();
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

    private Optional<RouteTicket> cachedTicket(UUID ticketId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.routeTicket(ticketId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            RouteTicket ticket = ticketFromJson(json);
            if (ticket.expiresAt().isBefore(Instant.now())) {
                deleteTicket(ticketId);
                deletePlayerTicket(ticket.playerUuid());
                return Optional.empty();
            }
            return Optional.of(ticket);
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private Optional<Map<String, Long>> cachedCountsByState() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.routeTicketCounts());
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(countsFromJson(json));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void cacheCountsByState(Map<String, Long> counts) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.routeTicketCounts(), countsJson(counts), "PX", Long.toString(COUNTS_CACHE_TTL_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void invalidateTicketCounts() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("DEL", RedisKeys.routeTicketCounts());
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private static RouteTicket ticketFromJson(String json) {
        return ticketFromObject(SimpleJson.object(SimpleJson.parse(json)));
    }

    private static RouteTicket ticketFromObject(Map<?, ?> root) {
        return new RouteTicket(
            uuid(root, "ticketId"),
            uuid(root, "playerUuid"),
            enumValue(RouteAction.class, root, "action", RouteAction.HOME),
            uuid(root, "islandId"),
            SimpleJson.text(root.get("targetNode")),
            SimpleJson.text(root.get("targetWorld")),
            enumValue(RouteTicketState.class, root, "state", RouteTicketState.FAILED),
            parseInstant(SimpleJson.text(root.get("expiresAt"))),
            SimpleJson.text(root.get("nonce")),
            payloadFromObject(SimpleJson.object(root.get("payload")))
        );
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    static Map<String, Long> countsFromJson(String json) {
        java.util.LinkedHashMap<String, Long> counts = zeroCounts();
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
        for (RouteTicketState state : RouteTicketState.values()) {
            counts.put(state.name(), routeTicketCount(root.get(state.name()), state));
        }
        return Map.copyOf(counts);
    }

    private static long routeTicketCount(Object value, RouteTicketState state) {
        if (value == null) {
            return 0L;
        }
        long count;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            count = ((Number) value).longValue();
        } else if (value instanceof java.math.BigInteger integer) {
            try {
                count = integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw invalidRouteTicketCount(state, value);
            }
        } else if (value instanceof java.math.BigDecimal decimal) {
            try {
                count = decimal.toBigIntegerExact().longValueExact();
            } catch (ArithmeticException exception) {
                throw invalidRouteTicketCount(state, value);
            }
        } else if (value instanceof Number number) {
            double decimal = number.doubleValue();
            count = number.longValue();
            if (!Double.isFinite(decimal) || decimal != count) {
                throw invalidRouteTicketCount(state, value);
            }
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                count = Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                throw invalidRouteTicketCount(state, value);
            }
        } else {
            throw invalidRouteTicketCount(state, value);
        }
        if (count < 0L) {
            throw invalidRouteTicketCount(state, value);
        }
        return count;
    }

    private static IllegalArgumentException invalidRouteTicketCount(RouteTicketState state, Object value) {
        return new IllegalArgumentException("invalid cached route ticket count for " + state.name() + ": " + SimpleJson.text(value));
    }

    private static String countsJson(Map<String, Long> counts) {
        java.util.LinkedHashMap<String, Long> values = new java.util.LinkedHashMap<>();
        for (RouteTicketState state : RouteTicketState.values()) {
            values.put(state.name(), counts.getOrDefault(state.name(), 0L));
        }
        return SimpleJson.stringify(values);
    }

    private static java.util.LinkedHashMap<String, Long> zeroCounts() {
        java.util.LinkedHashMap<String, Long> counts = new java.util.LinkedHashMap<>();
        for (RouteTicketState state : RouteTicketState.values()) {
            counts.put(state.name(), 0L);
        }
        return counts;
    }

    private static UUID uuid(Map<?, ?> root, String key) {
        try {
            return UUID.fromString(SimpleJson.text(root.get(key)));
        } catch (RuntimeException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Map<?, ?> root, String key, E fallback) {
        try {
            return Enum.valueOf(type, SimpleJson.text(root.get(key)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Map<String, String> payloadFromObject(Map<?, ?> root) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            values.put(SimpleJson.text(entry.getKey()), SimpleJson.text(entry.getValue()));
        }
        return Map.copyOf(values);
    }
}
