package kr.lunaf.cloudislands.coreservice.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditPayloadRedactorTest {
    @Test
    void auditPayloadRedactsSecretLikeKeysBeforeJsonOutput() {
        InMemoryAuditLogger audit = new InMemoryAuditLogger();

        audit.log(new UUID(0L, 1L), "ADMIN", "SECURITY_CHECK", "CORE", "runtime", Map.of(
            "admin-token", "admin-secret-value",
            "activationLockToken", "lock-token-value",
            "routeTicket", "ticket-value",
            "nonce", "nonce-value",
            "databasePassword", "database-secret-value",
            "storageAccessKey", "access-key-value",
            "operation", "cache-clear"
        ));

        String json = audit.toJson(10);
        assertTrue(json.contains("\"admin-token\":\"<redacted>\""));
        assertTrue(json.contains("\"activationLockToken\":\"<redacted>\""));
        assertTrue(json.contains("\"routeTicket\":\"<redacted>\""));
        assertTrue(json.contains("\"nonce\":\"<redacted>\""));
        assertTrue(json.contains("\"databasePassword\":\"<redacted>\""));
        assertTrue(json.contains("\"storageAccessKey\":\"<redacted>\""));
        assertTrue(json.contains("\"operation\":\"cache-clear\""));
        assertFalse(json.contains("admin-secret-value"));
        assertFalse(json.contains("lock-token-value"));
        assertFalse(json.contains("ticket-value"));
        assertFalse(json.contains("nonce-value"));
        assertFalse(json.contains("database-secret-value"));
        assertFalse(json.contains("access-key-value"));
    }

    @Test
    void auditPayloadJsonHandlesNullAndRedactsConsistently() {
        assertEquals("{}", AuditPayloadRedactor.payloadJson(null));

        String json = AuditPayloadRedactor.payloadJson(Map.of(
            "Authorization", "Bearer core-secret",
            "reason", "manual"
        ));

        assertTrue(json.contains("\"Authorization\":\"<redacted>\""));
        assertTrue(json.contains("\"reason\":\"manual\""));
        assertFalse(json.contains("core-secret"));
    }
}
