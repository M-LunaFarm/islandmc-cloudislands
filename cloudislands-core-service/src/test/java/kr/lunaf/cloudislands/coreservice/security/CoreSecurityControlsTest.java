package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpPrincipal;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreSecurityControlsTest {
    @Test
    void coreApiRequiresBearerToken() {
        ApiTokenGuard guard = new ApiTokenGuard("core-secret");

        assertFalse(guard.allowed(exchange("127.0.0.1")));
        assertFalse(guard.allowed(exchange("127.0.0.1", "Authorization", "Bearer wrong")));
        assertTrue(guard.allowed(exchange("127.0.0.1", "Authorization", "Bearer core-secret")));
        assertFalse(new ApiTokenGuard("").allowed(exchange("127.0.0.1", "Authorization", "Bearer core-secret")));
    }

    @Test
    void mtlsHeaderIsRequiredWhenEnabled() {
        MtlsHeaderGuard guard = new MtlsHeaderGuard(true, "X-SSL-Client-Verify", "SUCCESS");

        assertFalse(guard.allowed(exchange("127.0.0.1")));
        assertFalse(guard.allowed(exchange("127.0.0.1", "X-SSL-Client-Verify", "FAILED")));
        assertTrue(guard.allowed(exchange("127.0.0.1", "X-SSL-Client-Verify", "success")));
        assertFalse(guard.allowed(exchange("10.0.0.5", "X-SSL-Client-Verify", "success")));
        assertTrue(new MtlsHeaderGuard(false, "", "").allowed(exchange("127.0.0.1")));
    }

    @Test
    void ipAllowlistSupportsExactAndCidrEntries() {
        IpAllowlist allowlist = new IpAllowlist("127.0.0.1,10.0.0.0/8");

        assertTrue(allowlist.allowed(exchange("127.0.0.1")));
        assertTrue(allowlist.allowed(exchange("10.42.0.7")));
        assertFalse(allowlist.allowed(exchange("192.168.0.7")));
        assertTrue(new IpAllowlist("").allowed(exchange("192.168.0.7")));
    }

    @Test
    void rateLimitIsPerRemoteKey() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(Clock.systemUTC(), 2, 60_000L);

        assertTrue(limiter.allow("10.0.0.1"));
        assertTrue(limiter.allow("10.0.0.1"));
        assertFalse(limiter.allow("10.0.0.1"));
        assertTrue(limiter.allow("10.0.0.2"));
    }

    @Test
    void adminEndpointsRequireTokenAndSpecificPermission() {
        AdminEndpointGuard guard = new AdminEndpointGuard("admin-secret", true, "island-restore,audit-read");
        AdminEndpointGuard snapshotOnly = new AdminEndpointGuard("admin-secret", true, "island-snapshot");

        assertFalse(guard.allowed("/v1/admin/islands/restore", exchange("127.0.0.1")));
        assertFalse(snapshotOnly.allowed("/v1/admin/islands/restore", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "island-restore"
        )));
        assertTrue(guard.allowed("/v1/admin/islands/restore", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "island-restore"
        )));
        assertTrue(guard.allowed("/v1/audit", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "audit-read"
        )));
        assertFalse(new AdminEndpointGuard("admin-secret", false).allowed("/v1/admin/audit", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "*"
        )));
    }

    @Test
    void pathStyleAdminEndpointsKeepSpecificPermissions() {
        AdminEndpointGuard guard = new AdminEndpointGuard("admin-secret", true, "island-migrate,node-drain");
        AdminEndpointGuard auditOnly = new AdminEndpointGuard("admin-secret", true, "audit-read");
        String islandId = "00000000-0000-0000-0000-000000000201";

        assertFalse(auditOnly.allowed("/v1/admin/islands/" + islandId + "/migrate", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "island-migrate"
        )));
        assertTrue(guard.allowed("/v1/admin/islands/" + islandId + "/migrate", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "island-migrate"
        )));
        assertFalse(auditOnly.allowed("/v1/admin/nodes/island-2/drain", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "node-drain"
        )));
        assertTrue(guard.allowed("/v1/admin/nodes/island-2/drain", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "node-drain"
        )));
    }

    @Test
    void adminPermissionHeaderCannotEscalateServerSideTokenPolicy() {
        AdminEndpointGuard guard = new AdminEndpointGuard("admin-secret", true, "audit-read");

        assertFalse(guard.allowed("/v1/admin/islands/delete", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret",
            "X-CloudIslands-Admin-Permissions", "*"
        )));
    }

    @Test
    void adminPermissionDefaultsDoNotGrantWildcard() {
        AdminEndpointGuard guard = new AdminEndpointGuard("admin-secret", true);

        assertFalse(guard.allowed("/v1/admin/audit", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret"
        )));
    }

    @Test
    void unknownAdminEndpointsAreDeniedByDefaultEvenWithWildcardPolicy() {
        AdminEndpointGuard guard = new AdminEndpointGuard("admin-secret", true, "*");

        assertFalse(guard.allowed("/v1/admin/future-mutating-route", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret"
        )));
        assertTrue(guard.allowed("/v1/admin/config", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret"
        )));
    }

    @Test
    void auditJsonEscapesControlCharactersAndAcceptsEmptyPayload() {
        InMemoryAuditLogger audit = new InMemoryAuditLogger();

        audit.log(null, "API", "ISLAND\nDELETE", "ISLAND", "island\"one", null);
        audit.log(new UUID(0L, 42L), "ADMIN", "CACHE_CLEAR", "CACHE", "redis", Map.of(
            "reason", "quote\" newline\n tab\t slash\\"
        ));

        String json = audit.toJson(10);
        assertFalse(json.contains("\n"));
        assertTrue(json.contains("ISLAND\\nDELETE"));
        assertTrue(json.contains("quote\\\" newline\\n tab\\t slash\\\\"));
        assertTrue(json.contains("\"payload\":{}"));
    }

    private TestExchange exchange(String remoteAddress, String... headers) {
        TestExchange exchange = new TestExchange(remoteAddress);
        for (int index = 0; index + 1 < headers.length; index += 2) {
            exchange.getRequestHeaders().set(headers[index], headers[index + 1]);
        }
        return exchange;
    }

    private static final class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final InetSocketAddress remoteAddress;

        private TestExchange(String remoteAddress) {
            this.remoteAddress = new InetSocketAddress(remoteAddress, 25565);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return new Headers();
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/test");
        }

        @Override
        public String getRequestMethod() {
            return "GET";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return InputStream.nullInputStream();
        }

        @Override
        public OutputStream getResponseBody() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public int getResponseCode() {
            return 0;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
