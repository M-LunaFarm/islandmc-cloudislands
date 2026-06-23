package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class IpAllowlist {
    private final Set<String> exactAddresses;
    private final List<CidrBlock> cidrBlocks;

    public IpAllowlist(String csv) {
        if (csv == null || csv.isBlank()) {
            this.exactAddresses = Set.of();
            this.cidrBlocks = List.of();
        } else {
            List<String> entries = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
            this.exactAddresses = entries.stream()
                .filter(value -> !value.contains("/"))
                .map(IpAllowlist::stripBrackets)
                .collect(Collectors.toUnmodifiableSet());
            List<CidrBlock> blocks = new ArrayList<>();
            for (String entry : entries) {
                if (entry.contains("/")) {
                    CidrBlock.parse(entry).ifPresent(blocks::add);
                }
            }
            this.cidrBlocks = List.copyOf(blocks);
        }
    }

    public boolean allowed(HttpExchange exchange) {
        if (exactAddresses.isEmpty() && cidrBlocks.isEmpty()) {
            return true;
        }
        if (exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null) {
            return false;
        }
        InetAddress address = exchange.getRemoteAddress().getAddress();
        return allowed(address);
    }

    public boolean allowed(String ip) {
        if (exactAddresses.isEmpty() && cidrBlocks.isEmpty()) {
            return true;
        }
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) {
            return false;
        }
        try {
            return allowed(InetAddress.getByName(stripBrackets(ip)));
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private boolean allowed(InetAddress address) {
        String ip = address.getHostAddress();
        if (exactAddresses.contains(ip) || (address.isLoopbackAddress() && exactAddresses.contains("localhost"))) {
            return true;
        }
        for (CidrBlock block : cidrBlocks) {
            if (block.matches(address)) {
                return true;
            }
        }
        return false;
    }

    private static String stripBrackets(String value) {
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record CidrBlock(byte[] network, int prefixLength) {
        private static java.util.Optional<CidrBlock> parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length != 2) {
                return java.util.Optional.empty();
            }
            try {
                InetAddress address = InetAddress.getByName(stripBrackets(parts[0].trim()));
                int prefix = Integer.parseInt(parts[1].trim());
                int maxPrefix = address.getAddress().length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new CidrBlock(address.getAddress(), prefix));
            } catch (NumberFormatException | UnknownHostException ignored) {
                return java.util.Optional.empty();
            }
        }

        private boolean matches(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
