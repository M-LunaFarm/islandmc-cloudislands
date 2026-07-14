package kr.lunaf.cloudislands.paper.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperBootstrapStatusTest {
    @Test
    void tracksFailedAttemptRetryAndReadyTransitions() {
        PaperBootstrapStatus status = new PaperBootstrapStatus();

        assertEquals(PaperBootstrapStatus.State.STOPPED, status.snapshot().state());
        assertEquals(1, status.starting().attempt());
        PaperBootstrapStatus.Snapshot failed = status.failed(new IllegalStateException("configuration invalid"));
        assertEquals(PaperBootstrapStatus.State.FAILED, failed.state());
        assertTrue(failed.retryable());
        assertEquals("IllegalStateException", failed.failureType());

        assertEquals(2, status.starting().attempt());
        PaperBootstrapStatus.Snapshot ready = status.ready();
        assertEquals(PaperBootstrapStatus.State.READY, ready.state());
        assertFalse(ready.retryable());
        assertEquals(2, ready.attempt());

        assertEquals(PaperBootstrapStatus.State.STOPPED, status.stopped().state());
    }

    @Test
    void exposesRootCauseWithoutLeakingCredentials() {
        PaperBootstrapStatus status = new PaperBootstrapStatus();
        status.starting();

        PaperBootstrapStatus.Snapshot failed = status.failed(new PaperBootstrapException(
            "outer",
            new IllegalArgumentException("token=secret-value github_pat_ABC123 https://user:pass@example.test/path\ninvalid")
        ));

        assertEquals("IllegalArgumentException", failed.failureType());
        assertTrue(failed.failureMessage().contains("token=[redacted]"));
        assertTrue(failed.failureMessage().contains("[redacted-token]"));
        assertTrue(failed.failureMessage().contains("https://[redacted]@example.test/path invalid"));
        assertFalse(failed.failureMessage().contains("secret-value"));
        assertFalse(failed.failureMessage().contains("github_pat_ABC123"));
        assertFalse(failed.failureMessage().contains("user:pass"));
    }

    @Test
    void boundsFailureTextForChatAndLogs() {
        String sanitized = PaperBootstrapStatus.sanitize("x".repeat(400));

        assertEquals(240, sanitized.length());
        assertTrue(sanitized.endsWith("..."));
    }
}
