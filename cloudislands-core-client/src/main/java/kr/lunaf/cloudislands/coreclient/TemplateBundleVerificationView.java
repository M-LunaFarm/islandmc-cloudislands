package kr.lunaf.cloudislands.coreclient;

public record TemplateBundleVerificationView(
    boolean ok,
    String templateId,
    String bundleStoragePath,
    String bundleChecksum,
    long bundleSizeBytes
) {
    public TemplateBundleVerificationView {
        templateId = templateId == null ? "" : templateId;
        bundleStoragePath = bundleStoragePath == null ? "" : bundleStoragePath;
        bundleChecksum = bundleChecksum == null ? "" : bundleChecksum;
        bundleSizeBytes = Math.max(0L, bundleSizeBytes);
    }
}
