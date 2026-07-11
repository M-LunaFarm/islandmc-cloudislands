package kr.lunaf.cloudislands.coreservice.config;

import java.net.URI;
import java.net.URISyntaxException;

final class RedisUriResolver {
    private RedisUriResolver() {
    }

    static URI withPassword(URI redisUri, String password) {
        if (redisUri == null || password == null || password.isBlank()) {
            return redisUri;
        }
        if (redisUri.getUserInfo() != null && !redisUri.getUserInfo().isBlank()) {
            return redisUri;
        }
        try {
            return new URI(
                redisUri.getScheme(),
                password,
                redisUri.getHost(),
                redisUri.getPort(),
                redisUri.getPath(),
                redisUri.getQuery(),
                redisUri.getFragment()
            );
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Could not attach Redis password to configured URI", exception);
        }
    }
}
