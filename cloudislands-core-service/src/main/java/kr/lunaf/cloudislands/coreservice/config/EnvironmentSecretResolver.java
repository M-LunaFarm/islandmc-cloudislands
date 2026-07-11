package kr.lunaf.cloudislands.coreservice.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class EnvironmentSecretResolver {
    private EnvironmentSecretResolver() {
    }

    static String value(Map<String, String> environment, String key, String fallback) {
        String direct = environment.get(key);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        String secretFile = environment.get(key + "_FILE");
        if (secretFile == null || secretFile.isBlank()) {
            return fallback;
        }
        Path path = Path.of(secretFile.trim());
        try {
            String secret = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (secret.isBlank()) {
                throw new IllegalStateException("Secret file for " + key + " is empty: " + path);
            }
            return secret;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read secret file for " + key + ": " + path, exception);
        }
    }
}
