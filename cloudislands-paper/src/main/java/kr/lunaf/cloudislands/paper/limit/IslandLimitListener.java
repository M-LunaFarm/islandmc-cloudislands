package kr.lunaf.cloudislands.paper.limit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class IslandLimitListener implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 3_000L;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final MessageRenderer messages;
    private final Map<String, Long> lastLimitNotice = new ConcurrentHashMap<>();

    public IslandLimitListener(ProtectionController protection, IslandLimitCache limits) {
        this(protection, limits, null);
    }

    public IslandLimitListener(ProtectionController protection, IslandLimitCache limits, MessageRenderer messages) {
        this.protection = protection;
        this.limits = limits;
        this.messages = messages;
    }

    public void invalidate(UUID islandId) {
        String noticePrefix = islandId + ":";
        lastLimitNotice.keySet().removeIf(key -> key.startsWith(noticePrefix));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent) {
            return;
        }
        checkPlacements(event, List.of(event.getBlockPlaced().getType()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        checkPlacements(event, event.getReplacedBlockStates().stream()
            .map(state -> state.getBlock().getType())
            .toList());
    }

    private void checkPlacements(BlockPlaceEvent event, List<Material> materials) {
        Map<String, Long> additions = limitAdditions(materials);
        if (additions.isEmpty()) {
            return;
        }
        IslandRegion region = protection.regionAt(event.getBlockPlaced()).orElse(null);
        if (region == null) {
            return;
        }
        UUID islandId = region.islandId();
        for (Map.Entry<String, Long> addition : additions.entrySet()) {
            String key = addition.getKey();
            OptionalLong resolvedLimit = limits.limitIfReady(islandId, key, Long.MAX_VALUE);
            if (resolvedLimit.isEmpty()) {
                event.setCancelled(true);
                notifyLoading(event.getPlayer(), islandId, key);
                return;
            }
            long limit = resolvedLimit.getAsLong();
            if (limit == Long.MAX_VALUE) {
                continue;
            }
            OptionalLong resolvedCount = limits.blockCountIfReady(islandId, key);
            if (resolvedCount.isEmpty()) {
                event.setCancelled(true);
                notifyLoading(event.getPlayer(), islandId, key);
                return;
            }
            long current = resolvedCount.getAsLong();
            if (current >= limit || addition.getValue() > limit - current) {
                event.setCancelled(true);
                notifyLimit(event.getPlayer(), islandId, key, current, limit);
                return;
            }
        }
    }

    private Map<String, Long> limitAdditions(List<Material> materials) {
        Map<String, Long> additions = new LinkedHashMap<>();
        if (materials == null) {
            return additions;
        }
        for (Material material : materials) {
            additions.merge(GameplayParityPolicy.blockAmountLimitKey(material.getKey().toString()), 1L, Long::sum);
            String key = IslandBlockLimitKeys.limitKey(material);
            if (key != null) {
                additions.merge(key, 1L, Long::sum);
            }
        }
        return additions;
    }

    private void notifyLimit(Player player, UUID islandId, String key, long current, long limit) {
        if (!claimNotice(islandId, key)) {
            return;
        }
        player.sendMessage(message("limit-reached", "섬 {limit} 제한에 도달했습니다. 현재 {current}/{max}",
            "limit", limitName(key),
            "current", Long.toString(current),
            "max", Long.toString(limit)
        ));
    }

    private void notifyLoading(Player player, UUID islandId, String key) {
        if (!claimNotice(islandId, key + ":loading")) {
            return;
        }
        player.sendMessage(message("limit-data-loading", "섬 {limit} 한도 데이터를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.",
            "limit", limitName(key)
        ));
    }

    private boolean claimNotice(UUID islandId, String key) {
        String noticeKey = islandId + ":" + key;
        long now = System.currentTimeMillis();
        long previous = lastLimitNotice.getOrDefault(noticeKey, 0L);
        if (now - previous < NOTICE_COOLDOWN_MILLIS) {
            return false;
        }
        lastLimitNotice.put(noticeKey, now);
        return true;
    }

    private String message(String key, String fallback, String... variables) {
        if (messages == null) {
            return render(fallback, variables);
        }
        String rendered = messages.plain(key, variables);
        return rendered.isBlank() ? render(fallback, variables) : rendered;
    }

    private String render(String template, String... variables) {
        String rendered = template == null ? "" : template;
        for (int index = 0; index + 1 < variables.length; index += 2) {
            rendered = rendered.replace("{" + variables[index] + "}", variables[index + 1] == null ? "" : variables[index + 1]);
        }
        return rendered;
    }

    private String limitName(String key) {
        return switch (key) {
            case "HOPPER" -> "호퍼";
            case "SPAWNER" -> "스포너";
            case "REDSTONE" -> "레드스톤";
            default -> key;
        };
    }

}
