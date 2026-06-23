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
        Map<String, String> redactedPayload = AuditPayloadRedactor.redact(payload);
        delegate.log(actorUuid, actorType, action, targetType, targetId, redactedPayload);
        try {
            writer.xadd(
                stream,
                "actorUuid", actorUuid == null ? "" : actorUuid.toString(),
                "actorType", actorType == null ? "" : actorType,
                "action", action == null ? "" : action,
                "targetType", targetType == null ? "" : targetType,
                "targetId", targetId == null ? "" : targetId,
                "payload", AuditPayloadRedactor.payloadJson(redactedPayload),
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

}
