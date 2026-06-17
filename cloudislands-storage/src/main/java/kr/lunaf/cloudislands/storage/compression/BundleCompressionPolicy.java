package kr.lunaf.cloudislands.storage.compression;

import java.io.IOException;
import java.util.Locale;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;

public final class BundleCompressionPolicy {
    private BundleCompressionPolicy() {
    }

    public static String normalize(String compression) {
        if (compression == null || compression.isBlank()) {
            return BundleRestorePolicy.COMPRESSION;
        }
        return compression.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean supported(String compression) {
        return BundleRestorePolicy.COMPRESSION.equals(normalize(compression));
    }

    public static String bundleFileName(String compression) throws IOException {
        ensureSupported(compression, "bundle");
        return "bundle.tar.zst";
    }

    public static void ensureSupported(String compression, String source) throws IOException {
        String normalized = normalize(compression);
        if (!supported(normalized)) {
            throw new IOException("unsupported island bundle compression for " + source + ": " + normalized);
        }
    }
}
