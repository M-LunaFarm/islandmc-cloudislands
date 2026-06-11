package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.util.Set;

public final class IpAllowlist {
    private final Set<String> allowed;

    public IpAllowlist(String csv) {
        if (csv == null || csv.isBlank()) {
            this.allowed = Set.of();
        } else {
            this.allowed = Set.of(csv.split(","));
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
        return allowed.contains(ip);
    }
}
