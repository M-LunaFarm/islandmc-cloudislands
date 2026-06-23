package kr.lunaf.cloudislands.coreservice.config;

import static kr.lunaf.cloudislands.coreservice.config.CoreSetupSummary.coreJdbcFallbackReason;
import static kr.lunaf.cloudislands.coreservice.config.CoreSetupSummary.jdbcBackend;

import java.util.Locale;
import java.util.logging.Logger;

public final class CoreNetworkExposure {
    private CoreNetworkExposure() {
    }

    public static void logSecurityPosture(Logger logger, CoreServiceConfig config) {
        if (config.coreToken() == null || config.coreToken().isBlank()) {
            if (config.authMode().acceptsToken()) {
                logger.warning("CloudIslands security: Core API token is empty; token-authenticated requests will be rejected in mode " + config.authMode());
            }
        }
        logger.info("CloudIslands security: Core API auth mode is " + config.authMode());
        if (config.nodeCredentials() == null || config.nodeCredentials().isBlank()) {
            logger.warning("CloudIslands security: node credential bindings are not configured; node-scoped operations can only use legacy global-token compatibility");
        }
        if (config.adminApiEnabled() && (config.adminToken() == null || config.adminToken().isBlank())) {
            logger.warning("CloudIslands security: Admin API is enabled but admin token is empty; admin requests will be rejected");
        }
        if (config.adminListenerActive()) {
            logger.info("CloudIslands security: Admin listener is bound to " + config.adminBind() + ":" + config.adminPort());
        }
        if (!config.publicAdminApiEnabled()) {
            logger.info("CloudIslands security: Public Core listener rejects admin API paths");
        }
        if (!config.authMode().acceptsMtls()) {
            logger.warning("CloudIslands security: Core API mTLS verification is not accepted in auth mode " + config.authMode());
        }
        if ((config.ipAllowlist() == null || config.ipAllowlist().isBlank()) && publicBind(config.bind())) {
            logger.warning("CloudIslands security: Core API is bound to " + config.bind() + " without an IP allowlist");
        }
        if (publicBind(config.bind()) && config.allowInsecurePublicHttp()) {
            logger.warning("CloudIslands security: Core API plain HTTP public bind is explicitly allowed by insecure opt-in");
        }
        String jdbcFallbackReason = coreJdbcFallbackReason(config);
        if (jdbcFallbackReason != null && !jdbcFallbackReason.isBlank()) {
            logger.warning("CloudIslands setup: Core JDBC repositories are disabled: " + jdbcFallbackReason
                + " configuredDatabaseType=" + config.configuredDatabaseType()
                + " jdbcBackend=" + jdbcBackend(config.jdbcUrl()));
        }
        warnIfPublicHost(logger, "Redis", config.redisUri() == null ? "" : config.redisUri().getHost());
        if (config.jdbcRepositories() || config.jdbcJobs()) {
            warnIfPublicHost(logger, jdbcBackend(config.jdbcUrl()), jdbcHost(config.jdbcUrl()));
        }
        if ("S3".equalsIgnoreCase(config.storageType())) {
            String storageHost = config.storageEndpoint() == null ? "" : config.storageEndpoint().getHost();
            warnIfPublicHost(logger, "Object storage", storageHost);
            if (config.storageEndpoint() != null && "http".equalsIgnoreCase(config.storageEndpoint().getScheme()) && !internalHost(storageHost)) {
                logger.warning("CloudIslands security: Object storage endpoint uses plain HTTP on a non-internal host");
            }
        }
    }

    public static boolean coreApiAuthConfigured(CoreServiceConfig config) {
        return tokenAuthConfigured(config) || config.authMode().acceptsMtls();
    }

    public static boolean tokenAuthConfigured(CoreServiceConfig config) {
        if (!config.authMode().acceptsToken()) {
            return false;
        }
        return (config.coreToken() != null && !config.coreToken().isBlank())
            || (config.nodeCredentials() != null && !config.nodeCredentials().isBlank());
    }

    public static boolean publicBind(String bind) {
        return bind == null || bind.isBlank() || bind.equals("0.0.0.0") || bind.equals("::");
    }

    public static boolean internalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String value = host.toLowerCase(Locale.ROOT);
        if (value.equals("localhost") || value.endsWith(".local") || value.endsWith(".internal") || value.endsWith(".cluster.local")) {
            return true;
        }
        if (value.startsWith("127.") || value.startsWith("10.") || value.startsWith("192.168.")) {
            return true;
        }
        if (value.startsWith("172.")) {
            String[] parts = value.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    public static String jdbcHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        int scheme = jdbcUrl.indexOf("://");
        if (scheme < 0) {
            return "";
        }
        int hostStart = scheme + 3;
        int slash = jdbcUrl.indexOf('/', hostStart);
        String authority = slash < 0 ? jdbcUrl.substring(hostStart) : jdbcUrl.substring(hostStart, slash);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        return colon < 0 ? authority : authority.substring(0, colon);
    }

    private static void warnIfPublicHost(Logger logger, String name, String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        if (!internalHost(host)) {
            logger.warning("CloudIslands security: " + name + " host does not look internal: " + host);
        }
    }
}
