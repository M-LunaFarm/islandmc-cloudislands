package kr.lunaf.cloudislands.coreservice.config;

import java.net.URI;

public record StorageConfig(
    String type,
    URI endpoint,
    String bucket,
    String localPath,
    String region,
    String accessKey,
    String secretKey,
    String bearerToken
) {}
