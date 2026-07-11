package kr.lunaf.cloudislands.coreservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import org.junit.jupiter.api.Test;

class RedisUriResolverTest {
    @Test
    void attachesPasswordWithoutExposingItAsPlainUriSyntax() {
        URI resolved = RedisUriResolver.withPassword(URI.create("redis://redis:6379/2"), "p@ss:word/with spaces");

        assertEquals("redis", resolved.getScheme());
        assertEquals("redis", resolved.getHost());
        assertEquals(6379, resolved.getPort());
        assertEquals("/2", resolved.getPath());
        assertEquals("p@ss:word/with spaces", resolved.getUserInfo());
    }

    @Test
    void explicitUriCredentialsTakePriority() {
        URI configured = URI.create("redis://default:configured@redis:6379");

        assertEquals(configured, RedisUriResolver.withPassword(configured, "secret-file-value"));
    }

    @Test
    void leavesUriUnchangedWithoutPassword() {
        URI configured = URI.create("rediss://redis:6380");

        assertEquals(configured, RedisUriResolver.withPassword(configured, ""));
        assertNull(RedisUriResolver.withPassword(null, "password"));
    }
}
