package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpPrincipal;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.http.NodeScopedRequestGuard;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void nodeCredentialsBindBearerTokensToNodeIdentity() {
        ApiTokenGuard guard = new ApiTokenGuard("core-secret", NodeCredentialBindings.parse("node-a:token-a,node-b:token-b"));
        CoreApiAuthGuard auth = new CoreApiAuthGuard(CoreAuthMode.TOKEN_REQUIRED, guard, new MtlsHeaderGuard(false, "", ""));
        TestExchange nodeA = exchange(
            "127.0.0.1",
            "Authorization", "Bearer token-a",
            CoreApiIdentity.NODE_ID_HEADER, "node-a"
        );
        TestExchange wrongNode = exchange(
            "127.0.0.1",
            "Authorization", "Bearer token-a",
            CoreApiIdentity.NODE_ID_HEADER, "node-b"
        );
        TestExchange global = exchange("127.0.0.1", "Authorization", "Bearer core-secret");

        assertTrue(auth.allowed(nodeA));
        assertEquals("node-a", CoreApiIdentity.authenticatedNodeId(nodeA));
        assertTrue(CoreApiIdentity.nodeCredentialBindingConfigured(nodeA));
        assertFalse(auth.allowed(wrongNode));
        assertTrue(auth.allowed(global), "global token remains available for non-node Core clients");
        assertEquals("", CoreApiIdentity.authenticatedNodeId(global));
        assertTrue(CoreApiIdentity.nodeCredentialBindingConfigured(global));
    }

    @Test
    void nodeScopedRoutesRejectGlobalTokenWhenNodeCredentialsAreConfigured() throws Exception {
        ApiTokenGuard guard = new ApiTokenGuard("core-secret", NodeCredentialBindings.parse("node-a:token-a"));
        CoreApiAuthGuard auth = new CoreApiAuthGuard(CoreAuthMode.TOKEN_REQUIRED, guard, new MtlsHeaderGuard(false, "", ""));
        TestExchange global = exchange("127.0.0.1", "Authorization", "Bearer core-secret");
        TestExchange nodeA = exchange(
            "127.0.0.1",
            "Authorization", "Bearer token-a",
            CoreApiIdentity.NODE_ID_HEADER, "node-a"
        );

        assertTrue(auth.allowed(global));
        assertFalse(NodeScopedRequestGuard.allowNode(global, "node-a"));
        assertTrue(auth.allowed(nodeA));
        assertTrue(NodeScopedRequestGuard.allowNode(nodeA, "node-a"));
        assertFalse(NodeScopedRequestGuard.allowNode(nodeA, "node-b"));
    }

    @Test
    void nodeCredentialParserRejectsMalformedEntries() {
        assertThrows(IllegalArgumentException.class, () -> NodeCredentialBindings.parse("node-a"));
        assertThrows(IllegalArgumentException.class, () -> NodeCredentialBindings.parse("node-a:one,node-a:two"));
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
    void coreAuthModeSeparatesMtlsTokenAndEitherMode() {
        ApiTokenGuard tokenGuard = new ApiTokenGuard("core-secret");
        MtlsHeaderGuard mtlsGuard = new MtlsHeaderGuard(true, "X-SSL-Client-Verify", "SUCCESS");
        CoreApiAuthGuard mtlsRequired = new CoreApiAuthGuard(CoreAuthMode.MTLS_REQUIRED, tokenGuard, mtlsGuard);
        CoreApiAuthGuard tokenRequired = new CoreApiAuthGuard(CoreAuthMode.TOKEN_REQUIRED, tokenGuard, mtlsGuard);
        CoreApiAuthGuard either = new CoreApiAuthGuard(CoreAuthMode.MTLS_OR_TOKEN, tokenGuard, mtlsGuard);

        assertTrue(mtlsRequired.allowed(exchange("127.0.0.1", "X-SSL-Client-Verify", "SUCCESS")));
        assertFalse(mtlsRequired.allowed(exchange("127.0.0.1", "Authorization", "Bearer core-secret")));
        assertEquals("MTLS_REQUIRED", mtlsRequired.rejectCode());

        assertTrue(tokenRequired.allowed(exchange("127.0.0.1", "Authorization", "Bearer core-secret")));
        assertFalse(tokenRequired.allowed(exchange("127.0.0.1", "X-SSL-Client-Verify", "SUCCESS")));
        assertEquals("TOKEN_REQUIRED", tokenRequired.rejectCode());

        assertTrue(either.allowed(exchange("127.0.0.1", "Authorization", "Bearer core-secret")));
        assertTrue(either.allowed(exchange("127.0.0.1", "X-SSL-Client-Verify", "SUCCESS")));
        assertFalse(either.allowed(exchange("127.0.0.1")));
        assertEquals("MTLS_OR_TOKEN_REQUIRED", either.rejectCode());
    }

    @Test
    void coreAuthModeMigratesRequireMtlsBooleanAndRejectsUnknownModes() {
        assertEquals(CoreAuthMode.MTLS_REQUIRED, CoreAuthMode.fromConfig("", true));
        assertEquals(CoreAuthMode.TOKEN_REQUIRED, CoreAuthMode.fromConfig("", false));
        assertEquals(CoreAuthMode.MTLS_REQUIRED, CoreAuthMode.fromConfig("mtls-required", false));
        assertEquals(CoreAuthMode.MTLS_OR_TOKEN, CoreAuthMode.fromConfig("mtls-or-token", true));
        assertThrows(IllegalArgumentException.class, () -> CoreAuthMode.fromConfig("optional", true));
    }

    @Test
    void ipAllowlistSupportsExactAndCidrEntries() {
        IpAllowlist allowlist = new IpAllowlist("127.0.0.1,10.0.0.0/8");

        assertTrue(allowlist.allowed(exchange("127.0.0.1")));
        assertTrue(allowlist.allowed(exchange("10.42.0.7")));
        assertFalse(allowlist.allowed(exchange("192.168.0.7")));
        assertTrue(new IpAllowlist("203.0.113.0/24").allowed("203.0.113.7"));
        assertFalse(new IpAllowlist("203.0.113.0/24").allowed("198.51.100.7"));
        assertTrue(new IpAllowlist("").allowed(exchange("192.168.0.7")));
    }

    @Test
    void forwardedClientIpRequiresTrustedProxy() {
        ForwardedClientIpResolver resolver = new ForwardedClientIpResolver("127.0.0.1");

        ForwardedClientIpResolver.ClientIpResolution trusted = resolver.resolve(exchange(
            "127.0.0.1",
            "X-Forwarded-For", "203.0.113.7, 10.0.0.1"
        ));
        assertTrue(trusted.accepted());
        assertEquals("203.0.113.7", trusted.clientIp());
        assertEquals("127.0.0.1", trusted.remoteIp());
        ForwardedClientIpResolver.ClientIpResolution standard = resolver.resolve(exchange(
            "127.0.0.1",
            "Forwarded", "for=\"198.51.100.7:443\";proto=https, for=10.0.0.1"
        ));
        assertTrue(standard.accepted());
        assertEquals("198.51.100.7", standard.clientIp());

        ForwardedClientIpResolver.ClientIpResolution untrusted = resolver.resolve(exchange(
            "10.0.0.5",
            "X-Forwarded-For", "203.0.113.7"
        ));
        assertFalse(untrusted.accepted());
        assertEquals("FORWARDED_HEADER_UNTRUSTED", untrusted.rejectCode());

        ForwardedClientIpResolver.ClientIpResolution invalid = resolver.resolve(exchange(
            "127.0.0.1",
            "X-Forwarded-For", "client.example.com"
        ));
        assertFalse(invalid.accepted());
        assertEquals("FORWARDED_HEADER_INVALID", invalid.rejectCode());
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
    void publicAdminApiCanBeDisabledSeparatelyFromJobApi() {
        AdminEndpointGuard publicGuard = new AdminEndpointGuard("admin-secret", true, false, "audit-read,job-manage");

        assertFalse(publicGuard.allowed("/v1/admin/config", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret"
        )));
        assertFalse(publicGuard.allowed("/v1/audit", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret"
        )));
        assertTrue(publicGuard.allowed("/v1/jobs/claim", exchange(
            "127.0.0.1",
            "X-CloudIslands-Admin-Token", "admin-secret"
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
        private final Map<String, Object> attributes = new java.util.HashMap<>();

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
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
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
