package kr.lunaf.cloudislands.paper.command;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.failure.CoreApiDegradedModePolicy;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.coreclient.CoreMutationContext;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy;

final class IslandCommandRuntimeSupport {
    private IslandCommandRuntimeSupport() {
    }

    static <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.request(auditAction), operation);
    }

    static <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.idempotent(auditAction), operation);
    }

    static String coreWriteFailureMessage(boolean coreUnavailable, String maintenanceMessage, String fallback) {
        return coreUnavailable ? maintenanceMessage : fallback;
    }

    static String playerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 요청을 처리하지 못했습니다." : message;
        return PlayerRouteMessagePolicy.sanitize(value);
    }

    static boolean coreUnavailable(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof CoreApiException exception) {
            return exception.status() == 0 || exception.status() >= 500;
        }
        return current instanceof java.io.IOException
            || current instanceof java.net.ConnectException
            || current instanceof java.net.http.HttpTimeoutException
            || current instanceof java.net.http.HttpConnectTimeoutException;
    }

    static String maintenanceFallback() {
        return CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE;
    }
}
