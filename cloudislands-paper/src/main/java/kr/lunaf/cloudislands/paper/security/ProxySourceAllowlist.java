package kr.lunaf.cloudislands.paper.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProxySourceAllowlist {
    private final Set<String> exactAddresses;
    private final List<CidrBlock> cidrBlocks;

    public ProxySourceAllowlist(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            this.exactAddresses = Set.of();
            this.cidrBlocks = List.of();
            return;
        }
        List<String> normalized = entries.stream()
            .map(value -> value == null ? "" : value.trim())
            .filter(value -> !value.isBlank())
            .toList();
        this.exactAddresses = normalized.stream()
            .filter(value -> !value.contains("/"))
            .map(ProxySourceAllowlist::stripBrackets)
            .collect(Collectors.toUnmodifiableSet());
        List<CidrBlock> blocks = new ArrayList<>();
        for (String entry : normalized) {
            if (entry.contains("/")) {
                CidrBlock.parse(entry).ifPresent(blocks::add);
            }
        }
        this.cidrBlocks = List.copyOf(blocks);
    }

    public boolean configured() {
        return !exactAddresses.isEmpty() || !cidrBlocks.isEmpty();
    }

    public int entryCount() {
        return exactAddresses.size() + cidrBlocks.size();
    }

    public boolean allows(InetAddress address) {
        if (!configured()) {
            return true;
        }
        if (address == null) {
            return false;
        }
        String host = address.getHostAddress();
        if (exactAddresses.contains(host) || (address.isLoopbackAddress() && exactAddresses.contains("localhost"))) {
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
