package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;

public final class MtlsHeaderGuard {
    private final boolean required;
    private final String headerName;
    private final String expectedValue;

    public MtlsHeaderGuard(boolean required, String headerName, String expectedValue) {
        this.required = required;
        this.headerName = headerName == null || headerName.isBlank() ? "X-SSL-Client-Verify" : headerName;
        this.expectedValue = expectedValue == null || expectedValue.isBlank() ? "SUCCESS" : expectedValue;
    }

    public boolean allowed(HttpExchange exchange) {
        if (!required) {
            return true;
        }
        String value = exchange.getRequestHeaders().getFirst(headerName);
        return expectedValue.equalsIgnoreCase(value == null ? "" : value.trim());
    }
}
