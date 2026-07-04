package kr.lunaf.cloudislands.velocity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VelocityDiagnosticRedactorTest {
    @Test
    void redactsTokensSecretsPasswordsAndAuthorizationValues() {
        String redacted = VelocityDiagnosticRedactor.redact("{\"token\":\"ghp_example123456789\",\"password\":\"plain\",\"authorization\":\"Bearer secret\",\"secretKey\":\"minio-secret\"}");

        assertTrue(redacted.contains("token=***"));
        assertTrue(redacted.contains("password=***"));
        assertTrue(redacted.contains("authorization=***"));
        assertTrue(redacted.contains("secretKey=***"));
        assertFalse(redacted.contains("ghp_example123456789"));
        assertFalse(redacted.contains("plain"));
        assertFalse(redacted.contains("Bearer secret"));
        assertFalse(redacted.contains("minio-secret"));
    }
}
