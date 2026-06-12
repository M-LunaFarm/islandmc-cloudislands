package kr.lunaf.cloudislands.paper.economy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyBridge implements EconomyBridge {
    private final Server server;

    public VaultEconomyBridge(Server server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID playerUuid, BigDecimal amount, String reason) {
        return CompletableFuture.completedFuture(callBoolean(playerUuid, amount, "withdrawPlayer"));
    }

    @Override
    public CompletableFuture<Void> deposit(UUID playerUuid, BigDecimal amount, String reason) {
        if (callBoolean(playerUuid, amount, "depositPlayer")) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("economy deposit failed"));
    }

    @Override
    public CompletableFuture<BigDecimal> balance(UUID playerUuid) {
        Object economy = economy();
        if (economy == null) {
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }
        try {
            Method balance = economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            Object value = balance.invoke(economy, server.getOfflinePlayer(playerUuid));
            if (value instanceof Number number) {
                return CompletableFuture.completedFuture(BigDecimal.valueOf(number.doubleValue()));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return CompletableFuture.completedFuture(BigDecimal.ZERO);
    }

    private boolean callBoolean(UUID playerUuid, BigDecimal amount, String methodName) {
        Object economy = economy();
        if (economy == null || amount == null || amount.signum() <= 0) {
            return false;
        }
        try {
            Method method = economy.getClass().getMethod(methodName, OfflinePlayer.class, double.class);
            Object response = method.invoke(economy, server.getOfflinePlayer(playerUuid), amount.doubleValue());
            return transactionSuccess(response);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private boolean transactionSuccess(Object response) {
        if (response == null) {
            return false;
        }
        try {
            Object value = response.getClass().getMethod("transactionSuccess").invoke(response);
            if (value instanceof Boolean success) {
                return success;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object value = response.getClass().getField("transactionSuccess").get(response);
            return value instanceof Boolean success && success;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Object economy() {
        Class<?> economyClass;
        try {
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (ClassNotFoundException exception) {
            return null;
        }
        RegisteredServiceProvider<?> provider = server.getServicesManager().getRegistration(economyClass);
        return provider == null ? null : provider.getProvider();
    }
}
