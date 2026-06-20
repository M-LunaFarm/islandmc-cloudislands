package kr.lunaf.cloudislands.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

public final class ConfigSecretResolver {
    private ConfigSecretResolver() {
    }

    public static ResolvedSecret resolve(String value, Function<String, String> environment, Path baseDirectory) {
        String scalar = ConfigV2Validator.cleanScalar(value);
        if (scalar.startsWith("${env:") && scalar.endsWith("}")) {
            String name = scalar.substring("${env:".length(), scalar.length() - 1).trim();
            String resolved = environment == null ? null : environment.apply(name);
            if (resolved == null || resolved.isBlank()) {
                return ResolvedSecret.missing("MISSING_ENV", name);
            }
            return ResolvedSecret.value(resolved);
        }
        if (scalar.startsWith("${file:") && scalar.endsWith("}")) {
            String rawPath = scalar.substring("${file:".length(), scalar.length() - 1).trim();
            Path path = Path.of(rawPath);
            if (!path.isAbsolute()) {
                Path root = baseDirectory == null ? Path.of(".") : baseDirectory;
                path = root.resolve(path).normalize();
            }
            try {
                String resolved = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (resolved.isBlank()) {
                    return ResolvedSecret.missing("EMPTY_SECRET_FILE", path.toString());
                }
                return ResolvedSecret.value(resolved);
            } catch (IOException exception) {
                return ResolvedSecret.missing("MISSING_SECRET_FILE", path.toString());
            }
        }
        return ResolvedSecret.value(scalar);
    }

    public record ResolvedSecret(String value, ConfigIssue issue) {
        public ResolvedSecret {
            value = value == null ? "" : value;
        }

        public static ResolvedSecret value(String value) {
            return new ResolvedSecret(Objects.requireNonNullElse(value, ""), null);
        }

        public static ResolvedSecret missing(String code, String path) {
            return new ResolvedSecret("", new ConfigIssue(code, path, "secret reference could not be resolved"));
        }

        public boolean resolved() {
            return issue == null;
        }
    }
}
