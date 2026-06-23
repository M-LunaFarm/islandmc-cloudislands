package kr.lunaf.cloudislands.paper.bootstrap;

import java.net.URI;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;

public final class PaperCoreClientFactory {
    private PaperCoreClientFactory() {
    }

    public static CoreApiClient create(PaperRuntimeConfig.CoreApi config) {
        return create(config, "");
    }

    public static CoreApiClient create(PaperRuntimeConfig.CoreApi config, String nodeId) {
        PaperRuntimeConfig.CoreApi safeConfig = config == null ? PaperRuntimeConfig.CoreApi.defaults() : config;
        return new JdkCoreApiClient(
            URI.create(safeConfig.baseUrl()),
            safeConfig.token(),
            safeConfig.adminToken(),
            nodeId,
            safeConfig.timeout()
        );
    }
}
