package kr.lunaf.cloudislands.coreservice.template;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandTemplateRepository implements IslandTemplateRepository {
    private final IslandTemplateRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandTemplateRepository(IslandTemplateRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public Optional<IslandTemplateSnapshot> find(String templateId) {
        String id = normalize(templateId);
        Optional<List<IslandTemplateSnapshot>> cached = cached();
        if (cached.isPresent()) {
            return cached.get().stream()
                .filter(template -> template.id().equals(id))
                .findFirst();
        }
        Optional<IslandTemplateSnapshot> template = delegate.find(id);
        cache(delegate.list());
        return template;
    }

    @Override
    public List<IslandTemplateSnapshot> list() {
        Optional<List<IslandTemplateSnapshot>> cached = cached();
        if (cached.isPresent()) {
            return cached.get();
        }
        return cache(delegate.list());
    }

    @Override
    public IslandTemplateSnapshot upsert(IslandTemplateSnapshot template) {
        IslandTemplateSnapshot snapshot = delegate.upsert(template);
        cache(delegate.list());
        return snapshot;
    }

    @Override
    public boolean setEnabled(String templateId, boolean enabled) {
        boolean changed = delegate.setEnabled(templateId, enabled);
        if (changed) {
            cache(delegate.list());
        }
        return changed;
    }

    @Override
    public boolean delete(String templateId) {
        boolean deleted = delegate.delete(templateId);
        if (deleted) {
            cache(delegate.list());
        }
        return deleted;
    }

    @Override
    public boolean reorder(String templateId, int sortOrder) {
        boolean reordered = delegate.reorder(templateId, sortOrder);
        if (reordered) {
            cache(delegate.list());
        }
        return reordered;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private List<IslandTemplateSnapshot> cache(List<IslandTemplateSnapshot> templates) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.templates(), encode(templates));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return templates;
    }

    private Optional<List<IslandTemplateSnapshot>> cached() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.templates());
            return decodeCached(value);
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    static String encode(List<IslandTemplateSnapshot> templates) {
        StringBuilder out = new StringBuilder();
        for (IslandTemplateSnapshot template : templates) {
            out.append(encodeText(template.id())).append('|')
                .append(encodeText(template.displayName())).append('|')
                .append(encodeText(template.description())).append('|')
                .append(encodeText(template.category())).append('|')
                .append(template.enabled()).append('|')
                .append(encodeText(template.minNodeVersion())).append('|')
                .append(encodeText(template.requiredPermission())).append('|')
                .append(encodeText(template.iconMaterial())).append('|')
                .append(template.iconCustomModelData()).append('|')
                .append(encodeText(template.previewImageKey())).append('|')
                .append(encodeText(template.bundleStoragePath())).append('|')
                .append(encodeText(template.bundleChecksum())).append('|')
                .append(template.bundleSizeBytes()).append('|')
                .append(template.schemaVersion()).append('|')
                .append(template.defaultIslandSize()).append('|')
                .append(template.spawnWorldOffsetX()).append('|')
                .append(template.spawnWorldOffsetY()).append('|')
                .append(template.spawnWorldOffsetZ()).append('|')
                .append(template.spawnYaw()).append('|')
                .append(template.spawnPitch()).append('|')
                .append(encodeText(template.homeName())).append('|')
                .append(encodeText(template.environmentPreset())).append('|')
                .append(encodeText(template.biomeKey())).append('|')
                .append(encodeText(template.borderColor())).append('|')
                .append(encodeText(template.bankInitialBalance())).append('|')
                .append(encodeText(template.creationCost())).append('|')
                .append(template.sortOrder()).append('|')
                .append(encodeText(String.join(",", template.tags()))).append('|')
                .append(template.createdAt()).append('|')
                .append(template.updatedAt())
                .append('\n');
        }
        return out.toString();
    }

    static Optional<List<IslandTemplateSnapshot>> decodeCached(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        List<IslandTemplateSnapshot> parsed = parse(value);
        return parsed.isEmpty() ? Optional.empty() : Optional.of(parsed);
    }

    static List<IslandTemplateSnapshot> parse(String value) {
        List<IslandTemplateSnapshot> templates = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length == 4) {
                try {
                    templates.add(new IslandTemplateSnapshot(
                        decodeText(parts[0]),
                        decodeText(parts[1]),
                        Boolean.parseBoolean(parts[2]),
                        decodeText(parts[3])
                    ));
                } catch (RuntimeException ignored) {
                    // Skip corrupt Redis cache rows without discarding every cached template.
                }
                continue;
            }
            if (parts.length != 28 && parts.length != 30) {
                continue;
            }
            try {
                templates.add(new IslandTemplateSnapshot(
                    decodeText(parts[0]),
                    decodeText(parts[1]),
                    decodeText(parts[2]),
                    decodeText(parts[3]),
                    Boolean.parseBoolean(parts[4]),
                    decodeText(parts[5]),
                    decodeText(parts[6]),
                    decodeText(parts[7]),
                    intValue(parts[8]),
                    decodeText(parts[9]),
                    decodeText(parts[10]),
                    decodeText(parts[11]),
                    longValue(parts[12]),
                    intValue(parts[13]),
                    intValue(parts[14]),
                    doubleValue(parts[15]),
                    doubleValue(parts[16]),
                    doubleValue(parts[17]),
                    (float) doubleValue(parts[18]),
                    (float) doubleValue(parts[19]),
                    decodeText(parts[20]),
                    decodeText(parts[21]),
                    decodeText(parts[22]),
                    decodeText(parts[23]),
                    decodeText(parts[24]),
                    decodeText(parts[25]),
                    intValue(parts[26]),
                    tags(decodeText(parts[27])),
                    parts.length == 30 ? instant(parts[28]) : java.time.Instant.EPOCH,
                    parts.length == 30 ? instant(parts[29]) : java.time.Instant.EPOCH
                ));
            } catch (RuntimeException ignored) {
                // Skip corrupt Redis cache rows without discarding every cached template.
            }
        }
        return List.copyOf(templates);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encodedBase64) {
        return new String(Base64.getUrlDecoder().decode(encodedBase64), StandardCharsets.UTF_8);
    }

    private static int intValue(String value) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static double doubleValue(String value) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private static java.time.Instant instant(String value) {
        try {
            return java.time.Instant.parse(value);
        } catch (RuntimeException ignored) {
            return java.time.Instant.EPOCH;
        }
    }

    private static List<String> tags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(tag -> !tag.isBlank())
            .toList();
    }

    private static String normalize(String templateId) {
        return templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase();
    }
}
