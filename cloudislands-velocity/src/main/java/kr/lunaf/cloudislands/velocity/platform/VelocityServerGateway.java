package kr.lunaf.cloudislands.velocity.platform;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Locale;

public final class VelocityServerGateway {
    private final ProxyServer proxy;
    private final String islandPool;
    private final boolean hideNodeNames;

    public VelocityServerGateway(ProxyServer proxy, String islandPool, boolean hideNodeNames) {
        this.proxy = proxy;
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.hideNodeNames = hideNodeNames;
    }

    public int islandPoolServerCount() {
        if (proxy == null) {
            return 0;
        }
        int count = 0;
        for (RegisteredServer server : proxy.getAllServers()) {
            if (isIslandPoolServer(server.getServerInfo().getName())) {
                count++;
            }
        }
        return count;
    }

    public String islandPoolServerNames() {
        int count = islandPoolServerCount();
        if (hideNodeNames) {
            return count <= 0 ? "-" : "hidden(" + count + ")";
        }
        if (proxy == null) {
            return "-";
        }
        StringBuilder names = new StringBuilder();
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            if (!isIslandPoolServer(name)) {
                continue;
            }
            if (!names.isEmpty()) {
                names.append(',');
            }
            names.append(name);
        }
        return names.isEmpty() ? "-" : names.toString();
    }

    public String displayServerName(String serverName) {
        if (!hideNodeNames || !isIslandPoolServer(serverName)) {
            return serverName == null || serverName.isBlank() ? "-" : serverName;
        }
        return "island-pool";
    }

    public boolean isIslandPoolServer(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return false;
        }
        String normalizedServer = serverName.toLowerCase(Locale.ROOT);
        String normalizedPool = islandPool.toLowerCase(Locale.ROOT);
        return normalizedServer.equals(normalizedPool)
            || normalizedServer.startsWith(normalizedPool + "-")
            || normalizedServer.startsWith(normalizedPool + "_");
    }
}
