package kr.lunaf.cloudislands.storage;

import java.util.Optional;

public final class MinecraftKeyMigrations {
    private static final MinecraftKeyMigration DEFAULT = new DefaultMinecraftKeyMigration();

    private MinecraftKeyMigrations() {
    }

    public static MinecraftKeyMigration defaults() {
        return DEFAULT;
    }

    private static final class DefaultMinecraftKeyMigration implements MinecraftKeyMigration {
        @Override
        public Optional<String> migrateMaterial(String oldKey, int sourceDataVersion) {
            return normalizeMinecraftKey(oldKey);
        }

        @Override
        public Optional<String> migrateBiome(String oldKey, int sourceDataVersion) {
            return normalizeMinecraftKey(oldKey);
        }

        private Optional<String> normalizeMinecraftKey(String oldKey) {
            if (oldKey == null || oldKey.isBlank()) {
                return Optional.empty();
            }
            String key = oldKey.trim().toLowerCase(java.util.Locale.ROOT);
            if (key.indexOf(':') < 0) {
                key = "minecraft:" + key;
            }
            return isValidNamespaceKey(key) ? Optional.of(key) : Optional.empty();
        }

        private boolean isValidNamespaceKey(String key) {
            int separator = key.indexOf(':');
            return separator > 0
                && separator < key.length() - 1
                && validKeyPart(key.substring(0, separator), false)
                && validKeyPart(key.substring(separator + 1), true);
        }

        private boolean validKeyPart(String value, boolean allowSlash) {
            if (value.isBlank()) {
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                boolean valid = current >= 'a' && current <= 'z'
                    || current >= '0' && current <= '9'
                    || current == '_'
                    || current == '-'
                    || current == '.'
                    || allowSlash && current == '/';
                if (!valid) {
                    return false;
                }
            }
            return true;
        }
    }
}
