package kr.lunaf.cloudislands.api.integration;

import java.util.concurrent.CompletableFuture;

public interface CloudIntegrationPort {
    String pluginName();

    IntegrationCategory category();

    CompletableFuture<CloudIntegrationResult> apply(CloudIntegrationContext context, CloudIntegrationRequest request);
}
