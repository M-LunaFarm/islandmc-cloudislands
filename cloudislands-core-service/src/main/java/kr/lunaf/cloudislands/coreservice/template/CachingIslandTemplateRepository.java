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
    public IslandTemplateSnapshot upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        IslandTemplateSnapshot snapshot = delegate.upsert(templateId, displayName, enabled, minNodeVersion);
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
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(List<IslandTemplateSnapshot> templates) {
        StringBuilder out = new StringBuilder();
        for (IslandTemplateSnapshot template : templates) {
            out.append(encodeText(template.id())).append('|')
                .append(encodeText(template.displayName())).append('|')
                .append(template.enabled()).append('|')
                .append(encodeText(template.minNodeVersion()))
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandTemplateSnapshot> parse(String value) {
        List<IslandTemplateSnapshot> templates = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 4) {
                continue;
            }
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
        }
        return List.copyOf(templates);
    }

    private static String encodeText(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encodedBase64) {
        return new String(Base64.getUrlDecoder().decode(encodedBase64), StandardCharsets.UTF_8);
    }

    private static String normalize(String templateId) {
        return templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase();
    }
}
