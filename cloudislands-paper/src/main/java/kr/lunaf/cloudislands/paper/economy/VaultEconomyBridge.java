package kr.lunaf.cloudislands.paper.economy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.economy.EconomyProviderState;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultEconomyBridge implements EconomyBridge {
    private final Server server;
    private volatile boolean operationFailed;

    public VaultEconomyBridge(Server server) {
        this.server = server;
    }

    @Override
    public EconomyProviderState providerState() {
        Class<?> economyClass = economyClass();
        if (economyClass == null) {
            return EconomyProviderState.NOT_INSTALLED;
        }
        Object economy = economy(economyClass);
        if (economy == null) {
            return EconomyProviderState.DETECTED;
        }
        if (!apiCompatible(economy)) {
            return EconomyProviderState.DETECTED;
        }
        return operationFailed ? EconomyProviderState.OPERATION_FAILED : EconomyProviderState.ACTIVE;
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
        operationFailed = true;
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
            operationFailed = true;
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
            operationFailed = true;
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
        Class<?> economyClass = economyClass();
        return economyClass == null ? null : economy(economyClass);
    }

    private Class<?> economyClass() {
        Class<?> economyClass;
        try {
            economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (ClassNotFoundException exception) {
            return null;
        }
        return economyClass;
    }

    private Object economy(Class<?> economyClass) {
        if (server == null || economyClass == null) {
            return null;
        }
        RegisteredServiceProvider<?> provider = server.getServicesManager().getRegistration(economyClass);
        return provider == null ? null : provider.getProvider();
    }

    private boolean apiCompatible(Object economy) {
        try {
            economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
