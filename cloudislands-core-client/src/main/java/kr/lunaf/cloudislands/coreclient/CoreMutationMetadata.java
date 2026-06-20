package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;

public record CoreMutationMetadata(String requestId, String idempotencyKey, String auditAction) {
    public CoreMutationMetadata {
        requestId = clean(requestId);
        idempotencyKey = clean(idempotencyKey);
        auditAction = clean(auditAction);
    }

    public static CoreMutationMetadata request(String auditAction) {
        return new CoreMutationMetadata(UUID.randomUUID().toString(), "", auditAction);
    }

    public static CoreMutationMetadata idempotent(String auditAction, String idempotencyKey) {
        return new CoreMutationMetadata(UUID.randomUUID().toString(), idempotencyKey, auditAction);
    }

    public static CoreMutationMetadata idempotent(String auditAction) {
        String requestId = UUID.randomUUID().toString();
        return new CoreMutationMetadata(requestId, requestId, auditAction);
    }

    public boolean hasRequestId() {
        return !requestId.isBlank();
    }

    public boolean hasIdempotencyKey() {
        return !idempotencyKey.isBlank();
    }

    public boolean hasAuditAction() {
        return !auditAction.isBlank();
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('\n', '_').replace('\r', '_');
    }
}
