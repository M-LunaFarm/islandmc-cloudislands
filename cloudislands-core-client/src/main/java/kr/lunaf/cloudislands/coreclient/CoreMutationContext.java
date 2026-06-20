package kr.lunaf.cloudislands.coreclient;

import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class CoreMutationContext {
    public static final String REQUEST_ID_HEADER = "X-CloudIslands-Request-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String AUDIT_ACTION_HEADER = "X-CloudIslands-Audit-Action";
    private static final ThreadLocal<CoreMutationMetadata> CURRENT = new ThreadLocal<>();

    private CoreMutationContext() {
    }

    public static <T> CompletableFuture<T> with(CoreMutationMetadata metadata, Supplier<CompletableFuture<T>> operation) {
        CoreMutationMetadata previous = CURRENT.get();
        CURRENT.set(metadata);
        try {
            return operation.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static void apply(HttpRequest.Builder builder) {
        CoreMutationMetadata metadata = CURRENT.get();
        if (metadata == null) {
            return;
        }
        if (metadata.hasRequestId()) {
            builder.header(REQUEST_ID_HEADER, metadata.requestId());
        }
        if (metadata.hasIdempotencyKey()) {
            builder.header(IDEMPOTENCY_KEY_HEADER, metadata.idempotencyKey());
        }
        if (metadata.hasAuditAction()) {
            builder.header(AUDIT_ACTION_HEADER, metadata.auditAction());
        }
    }
}
