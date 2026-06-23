package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public final class ForwardedClientIpResolver {
    private final IpAllowlist trustedProxies;

    public ForwardedClientIpResolver(String trustedProxyAllowlist) {
        String allowlist = trustedProxyAllowlist == null || trustedProxyAllowlist.isBlank()
            ? "127.0.0.1,localhost,::1"
            : trustedProxyAllowlist;
        this.trustedProxies = new IpAllowlist(allowlist);
    }

    public ClientIpResolution resolve(HttpExchange exchange) {
        String remote = remoteIp(exchange);
        if (!hasForwardedHeaders(exchange)) {
            return ClientIpResolution.remote(remote);
        }
        if (!trustedProxies.allowed(exchange)) {
            return ClientIpResolution.rejected(remote, "FORWARDED_HEADER_UNTRUSTED");
        }
        String forwarded = forwardedFor(exchange);
        if (forwarded.isBlank() || !validIp(forwarded)) {
            return ClientIpResolution.rejected(remote, "FORWARDED_HEADER_INVALID");
        }
        return ClientIpResolution.forwarded(forwarded, remote);
    }

    private static boolean hasForwardedHeaders(HttpExchange exchange) {
        return header(exchange, "X-Forwarded-For") != null
            || header(exchange, "Forwarded") != null
            || header(exchange, "X-Real-IP") != null;
    }

    private static String forwardedFor(HttpExchange exchange) {
        String xForwardedFor = header(exchange, "X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return cleanIpToken(xForwardedFor.split(",", -1)[0]);
        }
        String forwarded = header(exchange, "Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            for (String hop : forwarded.split(",", -1)) {
                for (String part : hop.split(";", -1)) {
                    String trimmed = part.trim();
                    int equals = trimmed.indexOf('=');
                    if (equals > 0 && "for".equalsIgnoreCase(trimmed.substring(0, equals).trim())) {
                        return cleanIpToken(trimmed.substring(equals + 1));
                    }
                }
            }
        }
        return cleanIpToken(header(exchange, "X-Real-IP"));
    }

    private static String cleanIpToken(String value) {
        if (value == null) {
            return "";
        }
        String token = value.trim();
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 1) {
            token = token.substring(1, token.length() - 1).trim();
        }
        if (token.startsWith("[") && token.contains("]")) {
            return token.substring(1, token.indexOf(']'));
        }
        int colon = token.indexOf(':');
        if (colon > 0 && token.indexOf(':', colon + 1) < 0) {
            return token.substring(0, colon);
        }
        return token;
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange == null ? null : exchange.getRequestHeaders().getFirst(name);
    }

    private static String remoteIp(HttpExchange exchange) {
        InetSocketAddress remote = exchange == null ? null : exchange.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }

    private static boolean validIp(String value) {
        if (value == null || value.isBlank() || value.toLowerCase(Locale.ROOT).equals("unknown")) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (Character.digit(ch, 16) < 0 && ch != '.' && ch != ':') {
                return false;
            }
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    public record ClientIpResolution(boolean accepted, String clientIp, String remoteIp, String rejectCode) {
        private static ClientIpResolution remote(String remoteIp) {
            String ip = remoteIp == null ? "unknown" : remoteIp;
            return new ClientIpResolution(true, ip, ip, "");
        }

        private static ClientIpResolution forwarded(String clientIp, String remoteIp) {
            return new ClientIpResolution(true, clientIp, remoteIp == null ? "unknown" : remoteIp, "");
        }

        private static ClientIpResolution rejected(String remoteIp, String rejectCode) {
            return new ClientIpResolution(false, "", remoteIp == null ? "unknown" : remoteIp, rejectCode == null ? "FORWARDED_HEADER_REJECTED" : rejectCode);
        }
    }
}
