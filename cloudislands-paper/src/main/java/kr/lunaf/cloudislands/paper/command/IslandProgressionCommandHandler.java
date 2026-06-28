package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.BlockDetailsView;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.BlockDetailView;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.IslandLevelView;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.MissionCompletionResult;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.RankingEntryView;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.ReviewRankingEntryView;
import kr.lunaf.cloudislands.paper.application.IslandProgressionUseCase.UpgradePurchaseResult;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MissionView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.UpgradeView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandMissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRankingMenu;
import kr.lunaf.cloudislands.paper.gui.IslandUpgradeMenu;
import kr.lunaf.cloudislands.paper.generator.ConfigGeneratorRules;
import kr.lunaf.cloudislands.paper.generator.GeneratorInfoUseCase;
import kr.lunaf.cloudislands.paper.generator.GeneratorInfoUseCase.GeneratorInfoView;
import kr.lunaf.cloudislands.paper.generator.GeneratorInfoUseCase.GeneratorMaterialView;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandProgressionCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandProgressionUseCase progressionUseCase;
    private final GeneratorInfoUseCase generatorInfoUseCase;
    private final IslandLevelScanService levelScanService;
    private final Runtime runtime;

    IslandProgressionCommandHandler(Plugin plugin, CoreApiClient coreApiClient, IslandLevelScanService levelScanService, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.progressionUseCase = new IslandProgressionUseCase(coreApiClient);
        this.generatorInfoUseCase = new GeneratorInfoUseCase(coreApiClient, ConfigGeneratorRules.load(plugin));
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
        if (action instanceof GuiAction.RankingList rankingList) {
            listRanking(player, rankingList.worth(), 10);
            return true;
        }
        if (action instanceof GuiAction.MissionsOpen missionsOpen) {
            openMissionMenu(player, missionsOpen.kind());
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case RANKING_OPEN -> {
                    openRankingMenu(player);
                    yield true;
                }
                case LEVEL_RECALCULATE -> {
                    recalculateLevel(player);
                    yield true;
                }
                case LEVEL_SHOW -> {
                    showLevel(player);
                    yield true;
                }
                case WORTH_SHOW -> {
                    showWorth(player);
                    yield true;
                }
                case UPGRADES_OPEN -> {
                    openUpgradeMenu(player);
                    yield true;
                }
                case UPGRADES_LIST -> {
                    listUpgrades(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void showLevel(Player player) {
        runtime.currentIsland(player, "섬 안에서만 레벨을 확인할 수 있습니다.").ifPresent(islandId -> {
            progressionUseCase.islandLevel(islandId)
                .thenAccept(level -> runtime.message(player, "섬 레벨: " + level.level()))
                .exceptionally(error -> {
                    runtime.message(player, "섬 레벨을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showWorth(Player player) {
        runtime.currentIsland(player, "섬 안에서만 가치를 확인할 수 있습니다.").ifPresent(islandId -> {
            progressionUseCase.islandLevel(islandId)
                .thenAccept(level -> runtime.message(player, "섬 가치: " + level.worth()))
                .exceptionally(error -> {
                    runtime.message(player, "섬 가치를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showBlockDetails(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 블록 상세를 확인할 수 있습니다.").ifPresent(islandId -> {
            progressionUseCase.blockDetailsView(islandId, limit)
                .thenAccept(details -> runtime.message(player, blockDetailsMessage(details)))
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
            progressionUseCase.topWorthViews(cappedLimit)
                .thenAccept(rankings -> runtime.message(player, rankingMessage(rankings, "섬 가치 랭킹", "worth")))
                .exceptionally(error -> {
                    runtime.message(player, "섬 가치 랭킹을 불러오지 못했습니다.");
                    return null;
                });
            return;
        }
        progressionUseCase.topLevelViews(cappedLimit)
            .thenAccept(rankings -> runtime.message(player, rankingMessage(rankings, "섬 레벨 랭킹", "level")))
            .exceptionally(error -> {
                runtime.message(player, "섬 레벨 랭킹을 불러오지 못했습니다.");
                return null;
            });
    }

    private void listReviewRanking(Player player, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        progressionUseCase.topReviewViews(cappedLimit)
            .thenAccept(rankings -> runtime.message(player, reviewRankingMessage(rankings)))
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
            rescan.thenCompose(_ignored -> progressionUseCase.recalculateLevelView(islandId, player.getUniqueId()))
                .thenAccept(level -> runtime.message(player, "섬 레벨 계산 완료: 레벨 " + level.level() + " / 가치 " + level.worth()))
                .exceptionally(error -> {
                    runtime.message(player, "섬 레벨을 계산하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listUpgrades(Player player) {
        runtime.currentIsland(player, "섬 안에서만 업그레이드를 확인할 수 있습니다.").ifPresent(islandId -> {
            progressionUseCase.upgradeViews(islandId)
                .thenAccept(upgrades -> runtime.message(player, upgradeListMessage(upgrades)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 업그레이드를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showGenerator(Player player) {
        runtime.currentIsland(player, "섬 안에서만 생성기를 확인할 수 있습니다.").ifPresent(islandId -> {
            generatorInfoUseCase.view(islandId)
                .thenAccept(view -> runtime.message(player, generatorInfoMessage(view)))
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
            progressionUseCase.purchaseUpgradeResult(islandId, player.getUniqueId(), upgradeKey, runtime::mutateIdempotent)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), "섬 업그레이드를 구매하지 못했습니다."));
                        return;
                    }
                    runtime.message(player, upgradePurchaseMessage(result, upgradeKey));
                })
                .exceptionally(error -> {
                    runtime.message(player, "섬 업그레이드를 구매하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listMissions(Player player, String kind, String label) {
        runtime.currentIsland(player, "섬 안에서만 " + label + "을 확인할 수 있습니다.").ifPresent(islandId -> {
            progressionUseCase.missionViews(islandId, kind)
                .thenAccept(missions -> runtime.message(player, missionListMessage(missions, label)))
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
            progressionUseCase.completeMissionResult(islandId, player.getUniqueId(), missionKey, kind, runtime::mutateIdempotent)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), label + "을 완료하지 못했습니다."));
                        return;
                    }
                    runtime.message(player, missionCompletionMessage(result, missionKey, label));
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

    private static String rankingMessage(List<RankingEntryView> rankings, String label, String valueKey) {
        List<String> entries = new java.util.ArrayList<>();
        for (RankingEntryView ranking : rankings == null ? List.<RankingEntryView>of() : rankings) {
            if (entries.size() >= 10) {
                break;
            }
            String value = valueKey.equals("worth") ? ranking.worth() : Long.toString(ranking.level());
            String valueLabel = valueKey.equals("worth") ? "가치" : "레벨";
            entries.add((entries.size() + 1) + ". " + ranking.name() + " (ID=" + compactId(ranking.islandId()) + ", " + valueLabel + "=" + value + ")");
        }
        return entries.isEmpty() ? label + ": 기록이 없습니다." : label + ": " + String.join(" | ", entries);
    }

    private static String reviewRankingMessage(List<ReviewRankingEntryView> rankings) {
        List<String> entries = new java.util.ArrayList<>();
        for (ReviewRankingEntryView ranking : rankings == null ? List.<ReviewRankingEntryView>of() : rankings) {
            if (entries.size() >= 10) {
                break;
            }
            String rating = String.format(Locale.ROOT, "%.2f", ranking.averageRating());
            entries.add((entries.size() + 1) + ". ID=" + compactId(ranking.islandId()) + " 평점=" + rating + "/5 후기=" + ranking.reviewCount());
        }
        return entries.isEmpty() ? "섬 후기 랭킹: 기록이 없습니다." : "섬 후기 랭킹: " + String.join(" | ", entries);
    }

    private static String blockDetailsMessage(BlockDetailsView details) {
        if (details == null || details.blocks().isEmpty()) {
            return "섬 블록 기록이 없습니다.";
        }
        List<String> entries = new java.util.ArrayList<>();
        for (BlockDetailView block : details.blocks()) {
            if (entries.size() >= 20) {
                break;
            }
            entries.add(block.materialKey() + " x" + block.count() + " 가치=" + block.totalWorth() + " 점수=" + block.levelPoints());
        }
        return entries.isEmpty()
            ? "섬 블록 기록이 없습니다."
            : "섬 블록상세: 총가치=" + details.totalWorth() + " 총점수=" + details.totalLevelPoints() + " | " + String.join(" | ", entries);
    }

    private static String generatorInfoMessage(GeneratorInfoView view) {
        if (view == null || view.materials().isEmpty()) {
            return "섬 생성기: 규칙이 없습니다. / 업그레이드: /섬 업그레이드구매 generator";
        }
        List<String> entries = new java.util.ArrayList<>();
        int total = Math.max(1, view.totalWeight());
        for (GeneratorMaterialView material : view.materials()) {
            if (entries.size() >= 8) {
                break;
            }
            long percent = Math.round((material.weight() * 100.0D) / total);
            entries.add(material.materialKey() + "=" + percent + "%");
        }
        return "섬 생성기: key=" + view.generatorKey() + " level=" + view.level() + " | " + String.join(", ", entries) + " / 업그레이드: /섬 업그레이드구매 generator";
    }

    private static String upgradeListMessage(List<UpgradeView> upgrades) {
        List<String> entries = (upgrades == null ? List.<UpgradeView>of() : upgrades).stream()
            .map(upgrade -> upgrade.key() + " Lv." + upgrade.level())
            .toList();
        return entries.isEmpty() ? "섬 업그레이드가 없습니다." : "섬 업그레이드: " + String.join(", ", entries);
    }

    private static String missionListMessage(List<MissionView> missions, String label) {
        List<String> entries = (missions == null ? List.<MissionView>of() : missions).stream()
            .map(mission -> mission.key() + "(" + (mission.title().isBlank() ? mission.key() : mission.title()) + ", " + (mission.completed() ? "완료" : mission.progress() + "/" + mission.goal()) + ")")
            .toList();
        return entries.isEmpty() ? label + "이 없습니다." : label + ": " + String.join(", ", entries);
    }

    private static String upgradePurchaseMessage(UpgradePurchaseResult result, String fallbackKey) {
        String key = result.upgradeKey().isBlank() ? fallbackKey : result.upgradeKey();
        return "섬 업그레이드 구매 완료: " + key + " Lv." + result.level() + " / 비용 " + result.cost();
    }

    private static String missionCompletionMessage(MissionCompletionResult result, String fallbackKey, String label) {
        String title = result.title().isBlank() ? fallbackKey : result.title();
        return label + " 완료: " + title + (result.reward().isBlank() ? "" : " / 보상 " + result.reward());
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

    private static String compactId(String value) {
        if (value == null || value.length() <= 8) {
            return value == null ? "" : value;
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
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
