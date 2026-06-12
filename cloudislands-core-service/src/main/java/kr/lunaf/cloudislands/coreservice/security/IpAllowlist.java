package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class IpAllowlist {
    private final Set<String> allowed;

    public IpAllowlist(String csv) {
        if (csv == null || csv.isBlank()) {
            this.allowed = Set.of();
        } else {
            this.allowed = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        }
    }

    public boolean allowed(HttpExchange exchange) {
        if (allowed.isEmpty()) {
            return true;
        }
        if (exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null) {
            return false;
        }
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        return allowed.contains(ip)
            || (exchange.getRemoteAddress().getAddress().isLoopbackAddress() && allowed.contains("localhost"));
    }
}
