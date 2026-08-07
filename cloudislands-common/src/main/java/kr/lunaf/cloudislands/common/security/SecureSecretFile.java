package kr.lunaf.cloudislands.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;

/** OS-independent replacement for requiring administrators to run {@code openssl rand -hex}. */
public final class SecureSecretFile {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );

    private SecureSecretFile() {
    }

    public static Result loadOrCreate(Path path, int randomBytes) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("secret path is required");
        }
        String existing = read(path);
        if (!existing.isBlank()) {
            return new Result(existing, path.toAbsolutePath().normalize(), false);
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = new byte[Math.max(16, randomBytes)];
        RANDOM.nextBytes(bytes);
        String generated = HexFormat.of().formatHex(bytes);
        try {
            Files.writeString(path, generated + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            String raced = read(path);
            if (!raced.isBlank()) {
                return new Result(raced, path.toAbsolutePath().normalize(), false);
            }
            throw new IOException("secret file was concurrently created but is empty: " + path);
        }
        restrictToOwner(path);
        return new Result(generated, path.toAbsolutePath().normalize(), true);
    }

    public static String read(Path path) throws IOException {
        if (path == null || Files.notExists(path) || !Files.isRegularFile(path)) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8).trim();
    }

    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows ACLs and non-POSIX filesystems do not expose POSIX permissions.
        }
    }

    public record Result(String secret, Path path, boolean created) {
    }
}
