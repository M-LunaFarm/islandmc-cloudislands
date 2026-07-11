package kr.lunaf.cloudislands.paper.command;

import java.math.BigDecimal;
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
    private final IslandTargetResolver targetResolver;
    private final IslandLevelScanService levelScanService;
    private final Runtime runtime;

    IslandProgressionCommandHandler(Plugin plugin, CoreApiClient coreApiClient, IslandLevelScanService levelScanService, Runtime runtime) {
        this(plugin, coreApiClient, levelScanService, runtime, "default");
    }

    IslandProgressionCommandHandler(Plugin plugin, CoreApiClient coreApiClient, IslandLevelScanService levelScanService, Runtime runtime, String defaultGeneratorKey) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.progressionUseCase = new IslandProgressionUseCase(coreApiClient);
        this.generatorInfoUseCase = new GeneratorInfoUseCase(coreApiClient, ConfigGeneratorRules.load(plugin), defaultGeneratorKey);
        this.targetResolver = new IslandTargetResolver(coreApiClient);
        this.levelScanService = levelScanService;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("level") || subcommand.equals("레벨")) {
            showLevel(player);
            return true;
        }
        if (subcommand.equals("worth") || subcommand.equals("가치")) {
            showWorth(player);
            return true;
        }
        if (subcommand.equals("value")) {
            String material = args.length > 1 ? args[1] : player.getInventory().getItemInMainHand().getType().getKey().toString();
            showBlockValue(player, material);
            return true;
        }
        if (subcommand.equals("blocks") || subcommand.equals("values") || subcommand.equals("counts") || subcommand.equals("block-details") || subcommand.equals("block-counts") || subcommand.equals("블록상세") || subcommand.equals("블록목록")) {
            if (args.length > 1 && !isInteger(args[1])) {
                showBlockDetails(player, args[1], args.length > 2 ? integer(args[2], 10) : 10);
            } else {
                showBlockDetails(player, args.length > 1 ? integer(args[1], 10) : 10);
            }
            return true;
        }
        if (subcommand.equals("rank") || subcommand.equals("ranking") || subcommand.equals("top") || subcommand.equals("leaderboard") || subcommand.equals("랭킹")) {
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
        if (subcommand.equals("levelcalc") || subcommand.equals("recalculate") || subcommand.equals("recalc") || subcommand.equals("레벨계산")) {
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
        if (subcommand.equals("buyupgrade") || subcommand.equals("upgrade-buy") || subcommand.equals("rankup") || subcommand.equals("업그레이드구매")) {
            if (args.length < 2) {
                runtime.message(player, message("input-upgrade-key-required", "구매할 업그레이드 키를 입력해주세요."));
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
            listMissions(player, "MISSION", "progression-mission-label", "섬 미션");
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
            listMissions(player, "CHALLENGE", "progression-challenge-label", "섬 챌린지");
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.MissionComplete missionComplete) {
            completeTaskWithLabel(player, missionComplete.missionKey(), missionComplete.kind(), missionComplete.label());
            return true;
        }
        if (action instanceof GuiAction.UpgradePurchase upgradePurchase) {
            purchaseUpgrade(player, upgradePurchase.upgradeKey());
            return true;
        }
        if (action instanceof GuiAction.UpgradePage page) {
            IslandUpgradeMenu.open(plugin, coreApiClient, player, page.islandId(), runtime.messagesFor(player), page.page());
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
        if (action instanceof GuiAction.MissionPage page) {
            IslandMissionMenu.open(plugin, coreApiClient, player, page.islandId(), page.kind(), runtime.messagesFor(player), page.page());
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
        runtime.currentIsland(player, message("level-show-island-required", "섬 안에서만 레벨을 확인할 수 있습니다.")).ifPresent(islandId -> {
            progressionUseCase.islandLevel(islandId)
                .thenCombine(progressionUseCase.topLevelViews(100), (level, rankings) -> message("level-show-prefix", "섬 레벨: ") + level.level() + growthTargetSuffix(islandId, level.level(), level.worth(), rankings, "level"))
                .thenAccept(message -> runtime.message(player, message))
                .exceptionally(error -> {
                    runtime.message(player, message("level-load-failed", "섬 레벨을 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void showWorth(Player player) {
        runtime.currentIsland(player, message("worth-show-island-required", "섬 안에서만 가치를 확인할 수 있습니다.")).ifPresent(islandId -> {
            progressionUseCase.islandLevel(islandId)
                .thenCombine(progressionUseCase.topWorthViews(100), (level, rankings) -> message("worth-show-prefix", "섬 가치: ") + level.worth() + growthTargetSuffix(islandId, level.level(), level.worth(), rankings, "worth"))
                .thenAccept(message -> runtime.message(player, message))
                .exceptionally(error -> {
                    runtime.message(player, message("worth-load-failed", "섬 가치를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void showBlockValue(Player player, String material) {
        String normalized = BlockValueLookup.normalize(material);
        coreApiClient.blockValues().list()
            .thenAccept(values -> BlockValueLookup.find(values, normalized).ifPresentOrElse(
                value -> runtime.message(player, message("block-value-prefix", "블록 가치: ") + value.materialKey()
                    + " " + message("block-value-worth-label", "가치=") + value.worth()
                    + " " + message("block-value-level-label", "점수=") + value.levelPoints()
                    + " " + message("block-value-limit-label", "제한=") + value.limit()),
                () -> runtime.message(player, message("block-value-not-found-prefix", "등록되지 않은 블록입니다: ") + normalized)))
            .exceptionally(error -> {
                runtime.message(player, message("block-value-load-failed", "블록 가치를 불러오지 못했습니다."));
                return null;
            });
    }

    private void showBlockDetails(Player player, int limit) {
        runtime.currentIsland(player, message("block-details-island-required", "섬 안에서만 블록 상세를 확인할 수 있습니다.")).ifPresent(islandId -> {
            progressionUseCase.blockDetailsView(islandId, limit)
                .thenAccept(details -> runtime.message(player, blockDetailsMessage(details)))
                .exceptionally(error -> {
                    runtime.message(player, message("block-details-load-failed", "섬 블록 상세를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void showBlockDetails(Player player, String target, int limit) {
        targetResolver.resolve(target)
            .thenAccept(islandId -> {
                if (islandId == null) {
                    runtime.message(player, message("block-details-target-not-found", "대상 플레이어 또는 섬을 찾을 수 없습니다."));
                    return;
                }
                progressionUseCase.blockDetailsView(islandId, limit)
                    .thenAccept(details -> runtime.message(player, blockDetailsMessage(details)))
                    .exceptionally(error -> {
                        runtime.message(player, message("block-details-load-failed", "섬 블록 상세를 불러오지 못했습니다."));
                        return null;
                    });
            })
            .exceptionally(error -> {
                runtime.message(player, message("block-details-target-load-failed", "대상 섬을 확인하지 못했습니다."));
                return null;
            });
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void openRankingMenu(Player player) {
        IslandRankingMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
    }

    private void listRanking(Player player, boolean worthRanking, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        if (worthRanking) {
            progressionUseCase.topWorthViews(cappedLimit)
                .thenAccept(rankings -> runtime.message(player, rankingMessage(rankings, message("ranking-worth-title", "섬 가치 랭킹"), "worth")))
                .exceptionally(error -> {
                    runtime.message(player, message("ranking-worth-load-failed", "섬 가치 랭킹을 불러오지 못했습니다."));
                    return null;
                });
            return;
        }
        progressionUseCase.topLevelViews(cappedLimit)
            .thenAccept(rankings -> runtime.message(player, rankingMessage(rankings, message("ranking-level-title", "섬 레벨 랭킹"), "level")))
            .exceptionally(error -> {
                runtime.message(player, message("ranking-level-load-failed", "섬 레벨 랭킹을 불러오지 못했습니다."));
                return null;
            });
    }

    private void listReviewRanking(Player player, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        progressionUseCase.topReviewViews(cappedLimit)
            .thenAccept(rankings -> runtime.message(player, reviewRankingMessage(rankings)))
            .exceptionally(error -> {
                runtime.message(player, message("ranking-review-load-failed", "섬 후기 랭킹을 불러오지 못했습니다."));
                return null;
            });
    }

    private void recalculateLevel(Player player) {
        runtime.currentIsland(player, message("level-recalculate-island-required", "섬 안에서만 레벨을 계산할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.START_LEVEL_CALC)) {
                runtime.message(player, message("level-recalculate-denied", "섬 레벨을 계산할 권한이 없습니다."));
                return;
            }
            player.sendActionBar(Component.text(message("level-recalculate-started", "섬 블록을 다시 확인하는 중입니다.")));
            CompletableFuture<Void> rescan = levelScanService == null ? CompletableFuture.completedFuture(null) : levelScanService.rescanIsland(islandId);
            rescan.thenCompose(_ignored -> progressionUseCase.recalculateLevelView(islandId, player.getUniqueId()))
                .thenAccept(level -> runtime.message(player, message("level-recalculate-success-prefix", "섬 레벨 계산 완료: ")
                    + message("level-label", "레벨 ") + level.level()
                    + message("worth-separator-label", " / 가치 ") + level.worth()))
                .exceptionally(error -> {
                    runtime.message(player, message("level-recalculate-failed", "섬 레벨을 계산하지 못했습니다."));
                    return null;
                });
        });
    }

    private void listUpgrades(Player player) {
        runtime.currentIsland(player, message("upgrade-list-island-required", "섬 안에서만 업그레이드를 확인할 수 있습니다.")).ifPresent(islandId -> {
            progressionUseCase.upgradeViews(islandId)
                .thenAccept(upgrades -> runtime.message(player, upgradeListMessage(upgrades)))
                .exceptionally(error -> {
                    runtime.message(player, message("upgrade-list-load-failed", "섬 업그레이드를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void showGenerator(Player player) {
        runtime.currentIsland(player, message("generator-show-island-required", "섬 안에서만 생성기를 확인할 수 있습니다.")).ifPresent(islandId -> {
            generatorInfoUseCase.view(islandId)
                .thenAccept(view -> runtime.message(player, generatorInfoMessage(view)))
                .exceptionally(error -> {
                    runtime.message(player, message("generator-load-failed", "섬 생성기를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void openUpgradeMenu(Player player) {
        runtime.currentIsland(player, message("upgrade-menu-island-required", "섬 안에서만 업그레이드 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandUpgradeMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void purchaseUpgrade(Player player, String upgradeKey) {
        runtime.currentIsland(player, message("upgrade-purchase-island-required", "섬 안에서만 업그레이드를 구매할 수 있습니다.")).ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                runtime.message(player, message("upgrade-purchase-denied", "섬 업그레이드를 구매할 권한이 없습니다."));
                return;
            }
            progressionUseCase.purchaseUpgradeResult(islandId, player.getUniqueId(), upgradeKey, runtime::mutateIdempotent)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), message("upgrade-purchase-failed", "섬 업그레이드를 구매하지 못했습니다.")));
                        return;
                    }
                    runtime.message(player, upgradePurchaseMessage(result, upgradeKey));
                })
                .exceptionally(error -> {
                    runtime.message(player, message("upgrade-purchase-failed", "섬 업그레이드를 구매하지 못했습니다."));
                    return null;
                });
        });
    }

    private void listMissions(Player player, String kind, String labelKey, String labelFallback) {
        String label = message(labelKey, labelFallback);
        runtime.currentIsland(player, message("mission-list-island-required-prefix", "섬 안에서만 ") + label + message("mission-list-island-required-suffix", "을 확인할 수 있습니다.")).ifPresent(islandId -> {
            progressionUseCase.missionViews(islandId, kind)
                .thenAccept(missions -> runtime.message(player, missionListMessage(missions, label)))
                .exceptionally(error -> {
                    runtime.message(player, label + message("mission-list-load-failed-suffix", "을 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void openMissionMenu(Player player, String kind) {
        runtime.currentIsland(player, message("mission-menu-island-required", "섬 안에서만 과제 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandMissionMenu.open(plugin, coreApiClient, player, islandId, kind, runtime.messagesFor(player)));
    }

    private void completeMission(Player player, String missionKey) {
        completeTask(player, missionKey, "MISSION", "progression-mission-label", "섬 미션");
    }

    private void completeChallenge(Player player, String missionKey) {
        completeTask(player, missionKey, "CHALLENGE", "progression-challenge-label", "섬 챌린지");
    }

    private void completeTask(Player player, String missionKey, String kind, String labelKey, String labelFallback) {
        String label = message(labelKey, labelFallback);
        completeTaskWithLabel(player, missionKey, kind, label);
    }

    private void completeTaskWithLabel(Player player, String missionKey, String kind, String label) {
        runtime.currentIsland(player, message("mission-complete-island-required-prefix", "섬 안에서만 ") + label + message("mission-complete-island-required-suffix", "을 완료할 수 있습니다.")).ifPresent(islandId -> {
            progressionUseCase.completeMissionResult(islandId, player.getUniqueId(), missionKey, kind, runtime::mutateIdempotent)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        runtime.message(player, runtime.playerCodeMessage(result.code(), label + message("mission-complete-failed-suffix", "을 완료하지 못했습니다.")));
                        return;
                    }
                    runtime.message(player, missionCompletionMessage(result, missionKey, label));
                })
                .exceptionally(error -> {
                    runtime.message(player, label + message("mission-complete-failed-suffix", "을 완료하지 못했습니다."));
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

    private String rankingMessage(List<RankingEntryView> rankings, String label, String valueKey) {
        List<String> entries = new java.util.ArrayList<>();
        for (RankingEntryView ranking : rankings == null ? List.<RankingEntryView>of() : rankings) {
            if (entries.size() >= 10) {
                break;
            }
            String value = valueKey.equals("worth") ? ranking.worth() : Long.toString(ranking.level());
            String valueLabel = valueKey.equals("worth") ? message("ranking-worth-label", "가치") : message("ranking-level-label", "레벨");
            entries.add((entries.size() + 1) + ". " + ranking.name() + " (" + message("ranking-id-label", "ID=") + compactId(ranking.islandId()) + ", " + valueLabel + "=" + value + ")");
        }
        return entries.isEmpty() ? label + message("ranking-empty-suffix", ": 기록이 없습니다.") : label + message("ranking-title-suffix", ": ") + String.join(" | ", entries);
    }

    private String growthTargetSuffix(UUID islandId, long currentLevel, String currentWorth, List<RankingEntryView> rankings, String valueKey) {
        RankingEntryView target = nextGrowthTarget(islandId, currentLevel, currentWorth, rankings, valueKey);
        if (target == null) {
            return message("growth-target-none", " / 다음 목표: TOP100 기준 상위 목표 없음");
        }
        if ("worth".equals(valueKey)) {
            BigDecimal remaining = decimal(target.worth()).subtract(decimal(currentWorth)).max(BigDecimal.ZERO);
            return message("growth-target-worth-prefix", " / 다음 목표: 가치 +") + remaining.stripTrailingZeros().toPlainString() + " (" + target.name() + ")";
        }
        long remaining = Math.max(0L, target.level() - currentLevel);
        return message("growth-target-level-prefix", " / 다음 목표: 레벨 +") + remaining + " (" + target.name() + ")";
    }

    private static RankingEntryView nextGrowthTarget(UUID islandId, long currentLevel, String currentWorth, List<RankingEntryView> rankings, String valueKey) {
        String ownIslandId = islandId == null ? "" : islandId.toString();
        BigDecimal worth = decimal(currentWorth);
        for (RankingEntryView ranking : rankings == null ? List.<RankingEntryView>of() : rankings) {
            if (ranking.islandId().equalsIgnoreCase(ownIslandId)) {
                continue;
            }
            if ("worth".equals(valueKey)) {
                if (decimal(ranking.worth()).compareTo(worth) > 0) {
                    return ranking;
                }
            } else if (ranking.level() > currentLevel) {
                return ranking;
            }
        }
        return null;
    }

    private String reviewRankingMessage(List<ReviewRankingEntryView> rankings) {
        List<String> entries = new java.util.ArrayList<>();
        for (ReviewRankingEntryView ranking : rankings == null ? List.<ReviewRankingEntryView>of() : rankings) {
            if (entries.size() >= 10) {
                break;
            }
            String rating = String.format(Locale.ROOT, "%.2f", ranking.averageRating());
            entries.add((entries.size() + 1) + ". " + message("ranking-id-label", "ID=") + compactId(ranking.islandId())
                + " " + message("ranking-review-rating-label", "평점=") + rating + "/5 "
                + message("ranking-review-count-label", "후기=") + ranking.reviewCount());
        }
        return entries.isEmpty() ? message("ranking-review-title", "섬 후기 랭킹") + message("ranking-empty-suffix", ": 기록이 없습니다.") : message("ranking-review-title", "섬 후기 랭킹") + message("ranking-title-suffix", ": ") + String.join(" | ", entries);
    }

    private String blockDetailsMessage(BlockDetailsView details) {
        if (details == null || details.blocks().isEmpty()) {
            return message("block-details-empty", "섬 블록 기록이 없습니다.");
        }
        List<String> entries = new java.util.ArrayList<>();
        for (BlockDetailView block : details.blocks()) {
            if (entries.size() >= 20) {
                break;
            }
            entries.add(block.materialKey() + " x" + block.count() + " "
                + message("block-details-worth-label", "가치=") + block.totalWorth() + " "
                + message("block-details-points-label", "점수=") + block.levelPoints());
        }
        return entries.isEmpty()
            ? message("block-details-empty", "섬 블록 기록이 없습니다.")
            : message("block-details-prefix", "섬 블록상세: ")
                + message("block-details-total-worth-label", "총가치=") + details.totalWorth() + " "
                + message("block-details-total-points-label", "총점수=") + details.totalLevelPoints()
                + " | " + String.join(" | ", entries);
    }

    private String generatorInfoMessage(GeneratorInfoView view) {
        if (view == null || view.materials().isEmpty()) {
            return message("generator-empty", "섬 생성기: 규칙이 없습니다. / 업그레이드: /섬 업그레이드구매 generator");
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
        return message("generator-prefix", "섬 생성기: ")
            + message("generator-key-label", "key=") + view.generatorKey() + " "
            + message("generator-level-label", "level=") + view.level()
            + " | " + String.join(", ", entries)
            + message("generator-upgrade-hint", " / 업그레이드: /섬 업그레이드구매 generator");
    }

    private String upgradeListMessage(List<UpgradeView> upgrades) {
        List<String> entries = (upgrades == null ? List.<UpgradeView>of() : upgrades).stream()
            .map(upgrade -> upgrade.key() + " Lv." + upgrade.level())
            .toList();
        return entries.isEmpty() ? message("upgrade-list-empty", "섬 업그레이드가 없습니다.") : message("upgrade-list-prefix", "섬 업그레이드: ") + String.join(", ", entries);
    }

    private String missionListMessage(List<MissionView> missions, String label) {
        List<String> entries = (missions == null ? List.<MissionView>of() : missions).stream()
            .map(mission -> mission.key() + "(" + (mission.title().isBlank() ? mission.key() : mission.title()) + ", " + (mission.completed() ? message("mission-completed-label", "완료") : mission.progress() + "/" + mission.goal()) + ")")
            .toList();
        return entries.isEmpty() ? label + message("mission-list-empty-suffix", "이 없습니다.") : label + message("ranking-title-suffix", ": ") + String.join(", ", entries);
    }

    private String upgradePurchaseMessage(UpgradePurchaseResult result, String fallbackKey) {
        String key = result.upgradeKey().isBlank() ? fallbackKey : result.upgradeKey();
        return message("upgrade-purchase-success-prefix", "섬 업그레이드 구매 완료: ") + key + " Lv." + result.level() + message("upgrade-cost-label", " / 비용 ") + result.cost();
    }

    private String missionCompletionMessage(MissionCompletionResult result, String fallbackKey, String label) {
        String title = result.title().isBlank() ? fallbackKey : result.title();
        return label + message("mission-complete-success-suffix", " 완료: ") + title + (result.reward().isBlank() ? "" : message("mission-reward-label", " / 보상 ") + result.reward());
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
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

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value == null || value.isBlank() ? "0" : value.trim());
        } catch (RuntimeException ignored) {
            return BigDecimal.ZERO;
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
