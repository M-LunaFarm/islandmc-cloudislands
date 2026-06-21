package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandMissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRankingMenu;
import kr.lunaf.cloudislands.paper.gui.IslandUpgradeMenu;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandProgressionCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandLevelScanService levelScanService;
    private final Runtime runtime;

    IslandProgressionCommandHandler(Plugin plugin, CoreApiClient coreApiClient, IslandLevelScanService levelScanService, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.levelScanService = levelScanService;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("level") || subcommand.equals("레벨")) {
            showLevel(player);
            return true;
        }
        if (subcommand.equals("worth") || subcommand.equals("value") || subcommand.equals("가치")) {
            showWorth(player);
            return true;
        }
        if (subcommand.equals("blocks") || subcommand.equals("block-details") || subcommand.equals("block-counts") || subcommand.equals("블록상세") || subcommand.equals("블록목록")) {
            showBlockDetails(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("rank") || subcommand.equals("ranking") || subcommand.equals("랭킹")) {
            if (args.length > 1) {
                boolean reviewRanking = reviewRankingArg(args[1]);
                if (reviewRanking) {
                    listReviewRanking(player, rankingLimit(args, 2));
                    return true;
                }
                boolean worthRanking = args[1].equalsIgnoreCase("worth") || args[1].equals("가치");
                listRanking(player, worthRanking, rankingLimit(args, worthRanking ? 2 : 1));
            } else {
                openRankingMenu(player);
            }
            return true;
        }
        if (subcommand.equals("rank-list") || subcommand.equals("랭킹목록")) {
            if (args.length > 1 && reviewRankingArg(args[1])) {
                listReviewRanking(player, rankingLimit(args, 2));
                return true;
            }
            boolean worthRanking = args.length > 1 && (args[1].equalsIgnoreCase("worth") || args[1].equals("가치"));
            listRanking(player, worthRanking, rankingLimit(args, worthRanking ? 2 : 1));
            return true;
        }
        if (subcommand.equals("reviewrank") || subcommand.equals("평가랭킹") || subcommand.equals("후기랭킹")) {
            listReviewRanking(player, rankingLimit(args, 1));
            return true;
        }
        if (subcommand.equals("worthrank") || subcommand.equals("valuerank") || subcommand.equals("가치랭킹")) {
            listRanking(player, true, rankingLimit(args, 1));
            return true;
        }
        if (subcommand.equals("levelcalc") || subcommand.equals("recalculate") || subcommand.equals("레벨계산")) {
            recalculateLevel(player);
            return true;
        }
        if (subcommand.equals("upgrade") || subcommand.equals("upgrades") || subcommand.equals("업그레이드")) {
            if (args.length > 1) {
                purchaseUpgrade(player, args[1]);
            } else {
                openUpgradeMenu(player);
            }
            return true;
        }
        if (subcommand.equals("upgrade-menu")) {
            openUpgradeMenu(player);
            return true;
        }
        if (subcommand.equals("upgrade-list") || subcommand.equals("업그레이드목록")) {
            listUpgrades(player);
            return true;
        }
        if (subcommand.equals("buyupgrade") || subcommand.equals("upgrade-buy") || subcommand.equals("업그레이드구매")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-upgrade-key-required", "구매할 업그레이드 키를 입력해주세요."));
                return true;
            }
            purchaseUpgrade(player, args[1]);
            return true;
        }
        if (subcommand.equals("generator") || subcommand.equals("generator-info") || subcommand.equals("생성기") || subcommand.equals("생성기정보")) {
            showGenerator(player);
            return true;
        }
        if (subcommand.equals("mission") || subcommand.equals("missions") || subcommand.equals("미션")) {
            if (args.length > 1) {
                completeMission(player, args[1]);
            } else {
                openMissionMenu(player, "MISSION");
            }
            return true;
        }
        if (subcommand.equals("mission-menu")) {
            openMissionMenu(player, "MISSION");
            return true;
        }
        if (subcommand.equals("mission-list") || subcommand.equals("미션목록")) {
            listMissions(player, "MISSION", "섬 미션");
            return true;
        }
        if (subcommand.equals("challenge") || subcommand.equals("challenges") || subcommand.equals("챌린지")) {
            if (args.length > 1) {
                completeChallenge(player, args[1]);
            } else {
                openMissionMenu(player, "CHALLENGE");
            }
            return true;
        }
        if (subcommand.equals("challenge-menu")) {
            openMissionMenu(player, "CHALLENGE");
            return true;
        }
        if (subcommand.equals("challenge-list") || subcommand.equals("챌린지목록")) {
            listMissions(player, "CHALLENGE", "섬 챌린지");
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.MissionComplete missionComplete) {
            completeTask(player, missionComplete.missionKey(), missionComplete.kind(), missionComplete.label());
            return true;
        }
        if (action instanceof GuiAction.UpgradePurchase upgradePurchase) {
            purchaseUpgrade(player, upgradePurchase.upgradeKey());
            return true;
        }
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.ranking.open" -> {
                openRankingMenu(player);
                yield true;
            }
            case "island.ranking.list" -> {
                listRanking(player, data.getOrDefault("kind", "").equalsIgnoreCase("worth"), 10);
                yield true;
            }
            case "island.level.recalculate" -> {
                recalculateLevel(player);
                yield true;
            }
            case "island.level.show" -> {
                showLevel(player);
                yield true;
            }
            case "island.worth.show" -> {
                showWorth(player);
                yield true;
            }
            case "island.missions.open" -> {
                openMissionMenu(player, data.getOrDefault("kind", "MISSION"));
                yield true;
            }
            case "island.mission.complete" -> {
                completeTask(player, data.getOrDefault("missionKey", ""), data.getOrDefault("kind", "MISSION"), data.getOrDefault("label", "섬 미션"));
                yield true;
            }
            case "island.upgrades.open" -> {
                openUpgradeMenu(player);
                yield true;
            }
            case "island.upgrades.list" -> {
                listUpgrades(player);
                yield true;
            }
            case "island.upgrade.purchase" -> {
                purchaseUpgrade(player, data.getOrDefault("upgradeKey", ""));
                yield true;
            }
            default -> false;
        };
    }

    private void showLevel(Player player) {
        runtime.currentIsland(player, "섬 안에서만 레벨을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> runtime.message(player, "섬 레벨: " + (long) decimal(body, "level")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 레벨을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showWorth(Player player) {
        runtime.currentIsland(player, "섬 안에서만 가치를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> {
                    String worth = text(body, "worth");
                    runtime.message(player, "섬 가치: " + (worth.isBlank() ? "0" : worth));
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 가치를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showBlockDetails(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 블록 상세를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandBlockDetails(islandId, Math.max(1, Math.min(limit, 100)))
                .thenAccept(body -> runtime.message(player, blockDetailsMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 블록 상세를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openRankingMenu(Player player) {
        IslandRankingMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
    }

    private void listRanking(Player player, boolean worthRanking, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        if (worthRanking) {
            coreApiClient.topIslandsByWorth(cappedLimit)
                .thenAccept(body -> runtime.message(player, rankingMessage(body, "섬 가치 랭킹", "worth")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 가치 랭킹을 불러오지 못했습니다.");
                    return null;
                });
            return;
        }
        coreApiClient.topIslandsByLevel(cappedLimit)
            .thenAccept(body -> runtime.message(player, rankingMessage(body, "섬 레벨 랭킹", "level")))
            .exceptionally(error -> {
                runtime.message(player, "섬 레벨 랭킹을 불러오지 못했습니다.");
                return null;
            });
    }

    private void listReviewRanking(Player player, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        coreApiClient.topIslandsByReviews(cappedLimit)
            .thenAccept(body -> runtime.message(player, reviewRankingMessage(body)))
            .exceptionally(error -> {
                runtime.message(player, "섬 후기 랭킹을 불러오지 못했습니다.");
                return null;
            });
    }

    private void recalculateLevel(Player player) {
        runtime.currentIsland(player, "섬 안에서만 레벨을 계산할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.START_LEVEL_CALC)) {
                runtime.message(player, runtime.routeMessage("level-recalculate-denied", "섬 레벨을 계산할 권한이 없습니다."));
                return;
            }
            player.sendActionBar(Component.text(runtime.routeMessage("level-recalculate-started", "섬 블록을 다시 확인하는 중입니다.")));
            CompletableFuture<Void> rescan = levelScanService == null ? CompletableFuture.completedFuture(null) : levelScanService.rescanIsland(islandId);
            rescan.thenCompose(ignored -> coreApiClient.recalculateIslandLevel(islandId, player.getUniqueId()))
                .thenAccept(body -> {
                    String worth = text(body, "worth");
                    runtime.message(player, "섬 레벨 계산 완료: 레벨 " + (long) decimal(body, "level") + " / 가치 " + (worth.isBlank() ? "0" : worth));
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 레벨을 계산하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listUpgrades(Player player) {
        runtime.currentIsland(player, "섬 안에서만 업그레이드를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandUpgrades(islandId)
                .thenAccept(body -> runtime.message(player, upgradeListMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 업그레이드를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showGenerator(Player player) {
        runtime.currentIsland(player, "섬 안에서만 생성기를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandUpgrades(islandId)
                .thenAccept(body -> runtime.message(player, generatorInfoMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 생성기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openUpgradeMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 업그레이드 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandUpgradeMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void purchaseUpgrade(Player player, String upgradeKey) {
        runtime.currentIsland(player, "섬 안에서만 업그레이드를 구매할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                runtime.message(player, runtime.routeMessage("upgrade-purchase-denied", "섬 업그레이드를 구매할 권한이 없습니다."));
                return;
            }
            runtime.mutateIdempotent("island.upgrade.purchase", () -> coreApiClient.purchaseIslandUpgrade(islandId, player.getUniqueId(), upgradeKey))
                .thenAccept(body -> {
                    String key = text(body, "upgradeKey");
                    String cost = text(body, "cost");
                    if (body.contains("\"accepted\":false")) {
                        runtime.message(player, runtime.playerCodeMessage(text(body, "code"), "섬 업그레이드를 구매하지 못했습니다."));
                        return;
                    }
                    runtime.message(player, "섬 업그레이드 구매 완료: " + (key.isBlank() ? upgradeKey : key) + " Lv." + (long) decimal(body, "level") + " / 비용 " + (cost.isBlank() ? "0" : cost));
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 업그레이드를 구매하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listMissions(Player player, String kind, String label) {
        runtime.currentIsland(player, "섬 안에서만 " + label + "을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandMissions(islandId, kind)
                .thenAccept(body -> runtime.message(player, missionListMessage(body, label)))
                .exceptionally(error -> {
                    runtime.message(player, label + "을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openMissionMenu(Player player, String kind) {
        runtime.currentIsland(player, "섬 안에서만 과제 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandMissionMenu.open(plugin, coreApiClient, player, islandId, kind, runtime.messagesFor(player)));
    }

    private void completeMission(Player player, String missionKey) {
        completeTask(player, missionKey, "MISSION", "섬 미션");
    }

    private void completeChallenge(Player player, String missionKey) {
        completeTask(player, missionKey, "CHALLENGE", "섬 챌린지");
    }

    private void completeTask(Player player, String missionKey, String kind, String label) {
        runtime.currentIsland(player, "섬 안에서만 " + label + "을 완료할 수 있습니다.").ifPresent(islandId -> {
            runtime.mutateIdempotent("island.mission.complete", () -> coreApiClient.completeIslandMission(islandId, player.getUniqueId(), missionKey, kind))
                .thenAccept(body -> {
                    if (resultRejected(body)) {
                        runtime.message(player, runtime.playerCodeMessage(text(body, "code"), label + "을 완료하지 못했습니다."));
                        return;
                    }
                    String title = text(body, "title");
                    String reward = text(body, "reward");
                    runtime.message(player, label + " 완료: " + (title.isBlank() ? missionKey : title) + (reward.isBlank() ? "" : " / 보상 " + reward));
                })
                .exceptionally(error -> {
                    runtime.message(player, label + "을 완료하지 못했습니다.");
                    return null;
                });
        });
    }

    private static boolean reviewRankingArg(String value) {
        return value.equalsIgnoreCase("review") || value.equalsIgnoreCase("reviews") || value.equalsIgnoreCase("rating") || value.equals("후기") || value.equals("평가");
    }

    private static int rankingLimit(String[] args, int index) {
        if (args.length <= index) {
            return 10;
        }
        return (int) longValue(args[index], 10L);
    }

    private static String rankingMessage(String body, String label, String valueKey) {
        if (body == null || body.isBlank()) {
            return label + ": 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < body.length() && entries.size() < 10) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String name = text(object, "name");
                String value = valueKey.equals("worth") ? text(object, valueKey) : Long.toString((long) decimal(object, valueKey));
                String valueLabel = valueKey.equals("worth") ? "가치" : "레벨";
                entries.add((entries.size() + 1) + ". " + (name.isBlank() ? "이름 없는 섬" : name) + " (ID=" + compactId(islandId) + ", " + valueLabel + "=" + value + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? label + ": 기록이 없습니다." : label + ": " + String.join(" | ", entries);
    }

    private static String reviewRankingMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 후기 랭킹: 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < body.length() && entries.size() < 10) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String rating = String.format(Locale.ROOT, "%.2f", decimal(object, "averageRating"));
                long count = (long) decimal(object, "reviewCount");
                entries.add((entries.size() + 1) + ". ID=" + compactId(islandId) + " 평점=" + rating + "/5 후기=" + count);
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 후기 랭킹: 기록이 없습니다." : "섬 후기 랭킹: " + String.join(" | ", entries);
    }

    private static String blockDetailsMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 블록 기록이 없습니다.";
        }
        String totalWorth = text(body, "totalWorth");
        long totalLevelPoints = (long) decimal(body, "totalLevelPoints");
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"blocks\"");
        while (index >= 0 && index < body.length() && entries.size() < 20) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String materialKey = text(object, "materialKey");
            long count = (long) decimal(object, "count");
            String worth = text(object, "totalWorth");
            long points = (long) decimal(object, "levelPoints");
            if (!materialKey.isBlank()) {
                entries.add(materialKey + " x" + count + " 가치=" + (worth.isBlank() ? "0" : worth) + " 점수=" + points);
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty()
            ? "섬 블록 기록이 없습니다."
            : "섬 블록상세: 총가치=" + (totalWorth.isBlank() ? "0" : totalWorth) + " 총점수=" + totalLevelPoints + " | " + String.join(" | ", entries);
    }

    private static String generatorInfoMessage(String body) {
        String generatorKey = "default";
        long level = 1L;
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String upgradeKey = text(object, "upgradeKey");
            String normalized = upgradeKey.toLowerCase(Locale.ROOT);
            if (normalized.equals("generator") || normalized.startsWith("generator:")) {
                long currentLevel = Math.max(1L, (long) decimal(object, "level"));
                String currentKey = text(object, "generatorKey");
                if (currentKey.isBlank()) {
                    int separator = upgradeKey.indexOf(':');
                    currentKey = separator < 0 ? "default" : upgradeKey.substring(separator + 1);
                }
                if (currentLevel > level || (currentLevel == level && generatorKey.equals("default") && !currentKey.equalsIgnoreCase("default"))) {
                    level = currentLevel;
                    generatorKey = currentKey.isBlank() ? "default" : currentKey;
                }
            }
            index = objectEnd + 1;
        }
        return "섬 생성기: key=" + generatorKey + " level=" + level + " / 업그레이드: /섬 업그레이드구매 generator";
    }

    private static String upgradeListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String key = text(object, "upgradeKey");
            if (!key.isBlank()) {
                entries.add(key + " Lv." + (long) decimal(object, "level"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 업그레이드가 없습니다." : "섬 업그레이드: " + String.join(", ", entries);
    }

    private static String missionListMessage(String body, String label) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String key = text(object, "missionKey");
            if (!key.isBlank()) {
                String title = text(object, "title");
                String state = bool(object, "completed") ? "완료" : ((long) decimal(object, "progress") + "/" + (long) decimal(object, "goal"));
                entries.add(key + "(" + (title.isBlank() ? key : title) + ", " + state + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? label + "이 없습니다." : label + ": " + String.join(", ", entries);
    }

    private static boolean resultRejected(String body) {
        return body == null || body.isBlank() || body.contains("\"accepted\":false");
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? "" : unescape(json.substring(valueStart, end));
    }

    private static double decimal(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length()) {
            char current = json.charAt(end);
            if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.') {
                end++;
                continue;
            }
            break;
        }
        try {
            return Double.parseDouble(json.substring(valueStart, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private static boolean bool(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return false;
        }
        int valueStart = start + needle.length();
        return json.startsWith("true", valueStart);
    }

    private static String compactId(String value) {
        if (value == null || value.length() <= 8) {
            return value == null ? "" : value;
        }
        return value.substring(0, 8);
    }

    private static int jsonStringEnd(String value, int start) {
        boolean escaping = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return index;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                default -> builder.append(current);
            }
            escaping = false;
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);
    }
}
