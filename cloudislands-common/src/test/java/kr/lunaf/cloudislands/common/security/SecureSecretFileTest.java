package kr.lunaf.cloudislands.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecureSecretFileTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReusesAPlatformIndependentHexSecret() throws Exception {
        Path path = temporaryDirectory.resolve("forwarding.secret");

        SecureSecretFile.Result created = SecureSecretFile.loadOrCreate(path, 32);
        SecureSecretFile.Result reused = SecureSecretFile.loadOrCreate(path, 32);

        assertTrue(created.created());
        assertFalse(reused.created());
        assertEquals(64, created.secret().length());
        assertTrue(created.secret().matches("[0-9a-f]{64}"));
        assertEquals(created.secret(), reused.secret());
        assertEquals(created.secret(), Files.readString(path).trim());
    }
}
