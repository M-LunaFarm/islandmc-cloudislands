package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;

public final class MtlsHeaderGuard {
    private final boolean required;
    private final String headerName;
    private final String expectedValue;
    private final IpAllowlist trustedProxies;

    public MtlsHeaderGuard(boolean required, String headerName, String expectedValue) {
        this(required, headerName, expectedValue, "127.0.0.1,localhost,::1");
    }

    public MtlsHeaderGuard(boolean required, String headerName, String expectedValue, String trustedProxyAllowlist) {
        this.required = required;
        this.headerName = headerName == null || headerName.isBlank() ? "X-SSL-Client-Verify" : headerName;
        this.expectedValue = expectedValue == null || expectedValue.isBlank() ? "SUCCESS" : expectedValue;
        String allowlist = trustedProxyAllowlist == null || trustedProxyAllowlist.isBlank()
            ? "127.0.0.1,localhost,::1"
            : trustedProxyAllowlist;
        this.trustedProxies = new IpAllowlist(allowlist);
    }

    public boolean allowed(HttpExchange exchange) {
        if (!required) {
            return true;
        }
        String value = exchange.getRequestHeaders().getFirst(headerName);
        return expectedValue.equalsIgnoreCase(value == null ? "" : value.trim()) && trustedProxies.allowed(exchange);
    }

    public boolean required() {
        return required;
    }

    public boolean verified(HttpExchange exchange) {
        return required && allowed(exchange);
    }
}
