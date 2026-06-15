package kr.lunaf.cloudislands.coreservice.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreservice.redis.RedisStreamWriterAdapter;

public final class RedisAuditLogger implements AuditLogger {
    private final AuditLogger delegate;
    private final RedisStreamWriterAdapter writer;
    private final String stream;
    private final AtomicLong failures = new AtomicLong();

    public RedisAuditLogger(AuditLogger delegate, RedisStreamWriterAdapter writer, String stream) {
        this.delegate = delegate;
        this.writer = writer;
        this.stream = stream;
    }

    @Override
    public void log(UUID actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload) {
        delegate.log(actorUuid, actorType, action, targetType, targetId, payload);
        try {
            writer.xadd(
                stream,
                "actorUuid", actorUuid == null ? "" : actorUuid.toString(),
                "actorType", actorType == null ? "" : actorType,
                "action", action == null ? "" : action,
                "targetType", targetType == null ? "" : targetType,
                "targetId", targetId == null ? "" : targetId,
                "payload", payloadJson(payload),
                "createdAt", Instant.now().toString()
            );
        } catch (RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    @Override
    public String toJson(int limit) {
        return delegate.toJson(limit);
    }

    public long failuresTotal() {
        return failures.get();
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
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
