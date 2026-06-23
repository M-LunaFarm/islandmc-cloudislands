package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;

public final class CoreApiAuthGuard {
    private final CoreAuthMode mode;
    private final ApiTokenGuard tokenGuard;
    private final MtlsHeaderGuard mtlsGuard;

    public CoreApiAuthGuard(CoreAuthMode mode, ApiTokenGuard tokenGuard, MtlsHeaderGuard mtlsGuard) {
        this.mode = mode == null ? CoreAuthMode.MTLS_OR_TOKEN : mode;
        this.tokenGuard = tokenGuard;
        this.mtlsGuard = mtlsGuard;
    }

    public static CoreApiAuthGuard mtlsOrToken(ApiTokenGuard tokenGuard, MtlsHeaderGuard mtlsGuard) {
        return new CoreApiAuthGuard(CoreAuthMode.MTLS_OR_TOKEN, tokenGuard, mtlsGuard);
    }

    public boolean allowed(HttpExchange exchange) {
        boolean allowed = switch (mode) {
            case MTLS_REQUIRED -> mtlsAllowed(exchange);
            case TOKEN_REQUIRED -> tokenAllowed(exchange);
            case MTLS_OR_TOKEN -> mtlsAllowed(exchange) || tokenAllowed(exchange);
        };
        return allowed;
    }

    public String rejectCode() {
        return switch (mode) {
            case MTLS_REQUIRED -> "MTLS_REQUIRED";
            case TOKEN_REQUIRED -> "TOKEN_REQUIRED";
            case MTLS_OR_TOKEN -> "MTLS_OR_TOKEN_REQUIRED";
        };
    }

    public String rejectMessage() {
        return switch (mode) {
            case MTLS_REQUIRED -> "mTLS verification is required";
            case TOKEN_REQUIRED -> "Bearer API token authentication is required";
            case MTLS_OR_TOKEN -> "mTLS verification or Bearer API token authentication is required";
        };
    }

    public CoreAuthMode mode() {
        return mode;
    }

    private boolean tokenAllowed(HttpExchange exchange) {
        if (tokenGuard == null) {
            return false;
        }
        CoreApiAuthentication authentication = tokenGuard.authenticate(exchange);
        if (authentication.allowed()) {
            CoreApiIdentity.apply(exchange, authentication);
        }
        return authentication.allowed();
    }

    private boolean mtlsAllowed(HttpExchange exchange) {
        return mtlsGuard != null && mtlsGuard.verified(exchange);
    }
}
