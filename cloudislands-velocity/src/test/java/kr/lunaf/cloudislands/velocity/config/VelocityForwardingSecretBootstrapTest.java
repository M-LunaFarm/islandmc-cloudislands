package kr.lunaf.cloudislands.velocity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class VelocityForwardingSecretBootstrapTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reusesVelocityTomlPathAndCreatesSecretWithoutOpenSsl() throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("plugins").resolve("cloudislands");
        Files.createDirectories(dataDirectory);
        Files.writeString(temporaryDirectory.resolve("velocity.toml"), "forwarding-secret-file = \"network.secret\"\n");

        VelocityConfig first = VelocityConfigLoader.load(dataDirectory, LoggerFactory.getLogger(getClass()));
        VelocityConfig second = VelocityConfigLoader.load(dataDirectory, LoggerFactory.getLogger(getClass()));

        Path generated = temporaryDirectory.resolve("network.secret");
        assertTrue(Files.isRegularFile(generated));
        assertEquals(64, first.forwardingSecret().length());
        assertEquals(first.forwardingSecret(), second.forwardingSecret());
        assertEquals(first.forwardingSecret(), Files.readString(generated).trim());
    }
}
