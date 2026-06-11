package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class IslandCommandController implements CommandExecutor {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/" + label + " sethome [이름] 또는 /" + label + " setwarp <이름>");
            return true;
        }
        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("sethome") || subcommand.equals("셋홈")) {
            setHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("homes") || subcommand.equals("home-list") || subcommand.equals("홈목록")) {
            listHomes(player);
            return true;
        }
        if (subcommand.equals("home") || subcommand.equals("홈")) {
            teleportHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("warps") || subcommand.equals("warp-list") || subcommand.equals("워프")) {
            if (args.length > 1) {
                teleportWarp(player, args[1]);
            } else {
                listWarps(player);
            }
            return true;
        }
        if (subcommand.equals("warp")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            teleportWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("setwarp") || subcommand.equals("워프설정")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("delwarp") || subcommand.equals("deletewarp") || subcommand.equals("워프삭제")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            deleteWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("warp-public") || subcommand.equals("워프공개")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarpPublicAccess(player, args[1], true);
            return true;
        }
        if (subcommand.equals("warp-private") || subcommand.equals("워프비공개")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarpPublicAccess(player, args[1], false);
            return true;
        }
        if (subcommand.equals("public") || subcommand.equals("공개")) {
            setIslandPublicAccess(player, true);
            return true;
        }
        if (subcommand.equals("private") || subcommand.equals("비공개")) {
            setIslandPublicAccess(player, false);
            return true;
        }
        if (subcommand.equals("lock") || subcommand.equals("잠금")) {
            setIslandLocked(player, true);
            return true;
        }
        if (subcommand.equals("unlock") || subcommand.equals("잠금해제")) {
            setIslandLocked(player, false);
            return true;
        }
        if (subcommand.equals("level") || subcommand.equals("레벨")) {
            showIslandLevel(player);
            return true;
        }
        if (subcommand.equals("worth") || subcommand.equals("value") || subcommand.equals("가치")) {
            showIslandWorth(player);
            return true;
        }
        if (subcommand.equals("rank") || subcommand.equals("ranking") || subcommand.equals("랭킹")) {
            boolean worthRanking = args.length > 1 && (args[1].equalsIgnoreCase("worth") || args[1].equals("가치"));
            listIslandRanking(player, worthRanking);
            return true;
        }
        if (subcommand.equals("levelcalc") || subcommand.equals("recalculate") || subcommand.equals("레벨계산")) {
            recalculateIslandLevel(player);
            return true;
        }
        if (subcommand.equals("bank") || subcommand.equals("은행")) {
            showIslandBank(player);
            return true;
        }
        if (subcommand.equals("deposit") || subcommand.equals("bank-deposit") || subcommand.equals("입금")) {
            if (args.length < 2) {
                player.sendMessage("입금할 금액을 입력해주세요.");
                return true;
            }
            depositIslandBank(player, args[1]);
            return true;
        }
        if (subcommand.equals("withdraw") || subcommand.equals("bank-withdraw") || subcommand.equals("출금")) {
            if (args.length < 2) {
                player.sendMessage("출금할 금액을 입력해주세요.");
                return true;
            }
            withdrawIslandBank(player, args[1]);
            return true;
        }
        if (subcommand.equals("upgrade") || subcommand.equals("upgrades") || subcommand.equals("업그레이드")) {
            if (args.length > 1) {
                purchaseIslandUpgrade(player, args[1]);
            } else {
                listIslandUpgrades(player);
            }
            return true;
        }
        if (subcommand.equals("mission") || subcommand.equals("missions") || subcommand.equals("미션")) {
            if (args.length > 1) {
                completeIslandMission(player, args[1]);
            } else {
                listIslandMissions(player, "MISSION", "섬 미션");
            }
            return true;
        }
        if (subcommand.equals("challenge") || subcommand.equals("challenges") || subcommand.equals("챌린지")) {
            if (args.length > 1) {
                completeIslandChallenge(player, args[1]);
            } else {
                listIslandMissions(player, "CHALLENGE", "섬 챌린지");
            }
            return true;
        }
        if (subcommand.equals("chat") || subcommand.equals("islandchat") || subcommand.equals("채팅")) {
            if (args.length < 2) {
                player.sendMessage("섬 채팅 메시지를 입력해주세요.");
                return true;
            }
            sendIslandChat(player, "ISLAND", joined(args, 1), "섬 채팅");
            return true;
        }
        if (subcommand.equals("teamchat") || subcommand.equals("team-chat") || subcommand.equals("팀채팅")) {
            if (args.length < 2) {
                player.sendMessage("팀 채팅 메시지를 입력해주세요.");
                return true;
            }
            sendIslandChat(player, "TEAM", joined(args, 1), "팀 채팅");
            return true;
        }
        if (subcommand.equals("log") || subcommand.equals("logs") || subcommand.equals("로그")) {
            listIslandLogs(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("biome") || subcommand.equals("바이옴")) {
            if (args.length > 1) {
                setIslandBiome(player, args[1]);
            } else {
                showIslandBiome(player);
            }
            return true;
        }
        if (subcommand.equals("size") || subcommand.equals("크기")) {
            showIslandSize(player);
            return true;
        }
        if (subcommand.equals("border") || subcommand.equals("경계")) {
            showIslandBorder(player);
            return true;
        }
        if (subcommand.equals("limit") || subcommand.equals("limits") || subcommand.equals("limit-list") || subcommand.equals("제한") || subcommand.equals("제한목록")) {
            if (args.length > 2) {
                setIslandLimit(player, args[1], longValue(args[2], 0L));
            } else {
                listIslandLimits(player);
            }
            return true;
        }
        if (subcommand.equals("setlimit") || subcommand.equals("limit-set") || subcommand.equals("제한설정")) {
            if (args.length < 3) {
                player.sendMessage("제한 키와 값을 입력해주세요.");
                return true;
            }
            setIslandLimit(player, args[1], longValue(args[2], 0L));
            return true;
        }
        if (subcommand.equals("snapshot") || subcommand.equals("snapshots") || subcommand.equals("스냅샷")) {
            listIslandSnapshots(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("snapshot-create") || subcommand.equals("snapshot-request") || subcommand.equals("스냅샷생성")) {
            requestIslandSnapshot(player, args.length > 1 ? joined(args, 1) : "manual");
            return true;
        }
        if (subcommand.equals("snapshot-restore") || subcommand.equals("rollback") || subcommand.equals("스냅샷복원") || subcommand.equals("롤백")) {
            if (args.length < 2) {
                player.sendMessage("복원할 스냅샷 번호를 입력해주세요.");
                return true;
            }
            restoreIslandSnapshot(player, longValue(args[1], 0L));
            return true;
        }
        return false;
    }

    private void setHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈을 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_HOME)) {
                player.sendMessage("섬 홈을 설정할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, location(player.getLocation()))
                .thenRun(() -> message(player, "섬 홈을 설정했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 홈을 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프를 설정할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, location(player.getLocation()), false)
                .thenRun(() -> message(player, "섬 워프를 설정했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프를 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listHomes(Player player) {
        currentIsland(player, "섬 안에서만 홈 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandHomes(islandId)
                .thenAccept(body -> message(player, pointListMessage(body, "섬 홈", "섬 홈이 없습니다.")))
                .exceptionally(error -> {
                    message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listWarps(Player player) {
        currentIsland(player, "섬 안에서만 워프 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandWarps(islandId)
                .thenAccept(body -> message(player, pointListMessage(body, "섬 워프", "섬 워프가 없습니다.")))
                .exceptionally(error -> {
                    message(player, "섬 워프를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void teleportHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈으로 이동할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.INTERACT)) {
                player.sendMessage("섬 홈으로 이동할 권한이 없습니다.");
                return;
            }
            coreApiClient.listIslandHomes(islandId)
                .thenAccept(body -> teleport(player, point(body, name, player.getWorld().getName()), "홈을 찾을 수 없습니다.", "섬 홈으로 이동했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void teleportWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프로 이동할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandWarps(islandId)
                .thenAccept(body -> {
                    Point point = point(body, name, player.getWorld().getName());
                    if (point == null) {
                        teleport(player, null, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                        return;
                    }
                    coreApiClient.islandInfo(islandId).thenAccept(info -> {
                        if (!publicWarpAllowed(player, point, info) && !allowed(player, IslandPermission.INTERACT)) {
                            message(player, "섬 워프로 이동할 권한이 없습니다.");
                            return;
                        }
                        teleport(player, point, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                    }).exceptionally(error -> {
                        message(player, "섬 정보를 불러오지 못했습니다.");
                        return null;
                    });
                })
                .exceptionally(error -> {
                    message(player, "섬 워프를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void deleteWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프를 삭제할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프를 삭제할 권한이 없습니다.");
                return;
            }
            coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name)
                .thenRun(() -> message(player, "섬 워프를 삭제했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프를 삭제하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarpPublicAccess(Player player, String name, boolean publicAccess) {
        currentIsland(player, "섬 안에서만 워프 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프 공개 상태를 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess)
                .thenRun(() -> message(player, publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandPublicAccess(Player player, boolean publicAccess) {
        currentIsland(player, "섬 안에서만 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                player.sendMessage("섬 공개 상태를 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess)
                .thenRun(() -> message(player, publicAccess ? "섬을 공개했습니다." : "섬을 비공개로 변경했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandLocked(Player player, boolean locked) {
        currentIsland(player, "섬 안에서만 잠금 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                player.sendMessage("섬 잠금 상태를 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked)
                .thenRun(() -> message(player, locked ? "섬을 잠갔습니다." : "섬 잠금을 해제했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 잠금 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandLevel(Player player) {
        currentIsland(player, "섬 안에서만 레벨을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> message(player, "섬 레벨: " + (long) decimal(body, "level")))
                .exceptionally(error -> {
                    message(player, "섬 레벨을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandWorth(Player player) {
        currentIsland(player, "섬 안에서만 가치를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> {
                    String worth = text(body, "worth");
                    message(player, "섬 가치: " + (worth.isBlank() ? "0" : worth));
                })
                .exceptionally(error -> {
                    message(player, "섬 가치를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandRanking(Player player, boolean worthRanking) {
        if (worthRanking) {
            coreApiClient.topIslandsByWorth(10)
                .thenAccept(body -> message(player, rankingMessage(body, "섬 가치 랭킹", "worth")))
                .exceptionally(error -> {
                    message(player, "섬 가치 랭킹을 불러오지 못했습니다.");
                    return null;
                });
            return;
        }
        coreApiClient.topIslandsByLevel(10)
            .thenAccept(body -> message(player, rankingMessage(body, "섬 레벨 랭킹", "level")))
            .exceptionally(error -> {
                message(player, "섬 레벨 랭킹을 불러오지 못했습니다.");
                return null;
            });
    }

    private void recalculateIslandLevel(Player player) {
        currentIsland(player, "섬 안에서만 레벨을 계산할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.START_LEVEL_CALC)) {
                player.sendMessage("섬 레벨을 계산할 권한이 없습니다.");
                return;
            }
            coreApiClient.recalculateIslandLevel(islandId, player.getUniqueId())
                .thenAccept(body -> {
                    String worth = text(body, "worth");
                    message(player, "섬 레벨 계산 완료: 레벨 " + (long) decimal(body, "level") + " / 가치 " + (worth.isBlank() ? "0" : worth));
                })
                .exceptionally(error -> {
                    message(player, "섬 레벨을 계산하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandBank(Player player) {
        currentIsland(player, "섬 안에서만 은행을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandBank(islandId)
                .thenAccept(body -> message(player, "섬 은행 잔액: " + bankBalance(body)))
                .exceptionally(error -> {
                    message(player, "섬 은행을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void depositIslandBank(Player player, String amount) {
        currentIsland(player, "섬 안에서만 은행에 입금할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.DEPOSIT_BANK)) {
                player.sendMessage("섬 은행에 입금할 권한이 없습니다.");
                return;
            }
            coreApiClient.depositIslandBank(islandId, player.getUniqueId(), amount)
                .thenAccept(body -> message(player, "섬 은행에 입금했습니다. 잔액: " + bankBalance(body)))
                .exceptionally(error -> {
                    message(player, "섬 은행에 입금하지 못했습니다.");
                    return null;
                });
        });
    }

    private void withdrawIslandBank(Player player, String amount) {
        currentIsland(player, "섬 안에서만 은행에서 출금할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.WITHDRAW_BANK)) {
                player.sendMessage("섬 은행에서 출금할 권한이 없습니다.");
                return;
            }
            coreApiClient.withdrawIslandBank(islandId, player.getUniqueId(), amount)
                .thenAccept(body -> {
                    if (body.contains("\"accepted\":false")) {
                        message(player, "섬 은행에서 출금하지 못했습니다. 잔액: " + bankBalance(body));
                        return;
                    }
                    message(player, "섬 은행에서 출금했습니다. 잔액: " + bankBalance(body));
                })
                .exceptionally(error -> {
                    message(player, "섬 은행에서 출금하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandUpgrades(Player player) {
        currentIsland(player, "섬 안에서만 업그레이드를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandUpgrades(islandId)
                .thenAccept(body -> message(player, upgradeListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 업그레이드를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void purchaseIslandUpgrade(Player player, String upgradeKey) {
        currentIsland(player, "섬 안에서만 업그레이드를 구매할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                player.sendMessage("섬 업그레이드를 구매할 권한이 없습니다.");
                return;
            }
            coreApiClient.purchaseIslandUpgrade(islandId, player.getUniqueId(), upgradeKey)
                .thenAccept(body -> {
                    String key = text(body, "upgradeKey");
                    String cost = text(body, "cost");
                    if (body.contains("\"accepted\":false")) {
                        message(player, "섬 업그레이드를 구매하지 못했습니다: " + text(body, "code"));
                        return;
                    }
                    message(player, "섬 업그레이드 구매 완료: " + (key.isBlank() ? upgradeKey : key) + " Lv." + (long) decimal(body, "level") + " / 비용 " + (cost.isBlank() ? "0" : cost));
                })
                .exceptionally(error -> {
                    message(player, "섬 업그레이드를 구매하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandMissions(Player player, String kind, String label) {
        currentIsland(player, "섬 안에서만 " + label + "을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandMissions(islandId, kind)
                .thenAccept(body -> message(player, missionListMessage(body, label)))
                .exceptionally(error -> {
                    message(player, label + "을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void completeIslandMission(Player player, String missionKey) {
        completeIslandTask(player, missionKey, "섬 미션");
    }

    private void completeIslandChallenge(Player player, String missionKey) {
        completeIslandTask(player, missionKey, "섬 챌린지");
    }

    private void completeIslandTask(Player player, String missionKey, String label) {
        currentIsland(player, "섬 안에서만 " + label + "을 완료할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.completeIslandMission(islandId, player.getUniqueId(), missionKey)
                .thenAccept(body -> {
                    String title = text(body, "title");
                    String reward = text(body, "reward");
                    message(player, label + " 완료: " + (title.isBlank() ? missionKey : title) + (reward.isBlank() ? "" : " / 보상 " + reward));
                })
                .exceptionally(error -> {
                    message(player, label + "을 완료하지 못했습니다.");
                    return null;
                });
        });
    }

    private void sendIslandChat(Player player, String channel, String chatMessage, String label) {
        currentIsland(player, "섬 안에서만 " + label + "을 사용할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, chatMessage)
                .thenAccept(body -> message(player, label + " 전송: " + text(body, "message")))
                .exceptionally(error -> {
                    message(player, label + "을 전송하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandLogs(Player player, int limit) {
        currentIsland(player, "섬 안에서만 로그를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandLogs(islandId, Math.max(1, Math.min(limit, 30)))
                .thenAccept(body -> message(player, logListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 로그를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandBiome(Player player) {
        currentIsland(player, "섬 안에서만 바이옴을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandBiome(islandId)
                .thenAccept(body -> message(player, "섬 바이옴: " + text(body, "biomeKey")))
                .exceptionally(error -> {
                    message(player, "섬 바이옴을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandBiome(Player player, String biomeKey) {
        currentIsland(player, "섬 안에서만 바이옴을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_BIOME)) {
                player.sendMessage("섬 바이옴을 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey)
                .thenRun(() -> message(player, "섬 바이옴을 변경했습니다: " + biomeKey))
                .exceptionally(error -> {
                    message(player, "섬 바이옴을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandSize(Player player) {
        currentIsland(player, "섬 안에서만 크기를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> message(player, "섬 크기: " + (long) decimal(body, "size")))
                .exceptionally(error -> {
                    message(player, "섬 크기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandBorder(Player player) {
        currentIsland(player, "섬 안에서만 경계를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> message(player, "섬 경계: " + (long) decimal(body, "border")))
                .exceptionally(error -> {
                    message(player, "섬 경계를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandLimits(Player player) {
        currentIsland(player, "섬 안에서만 제한을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandLimits(islandId)
                .thenAccept(body -> message(player, limitListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 제한을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandLimit(Player player, String limitKey, long value) {
        currentIsland(player, "섬 안에서만 제한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                player.sendMessage("섬 제한을 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandLimit(islandId, player.getUniqueId(), limitKey, value)
                .thenAccept(body -> message(player, "섬 제한 변경 완료: " + text(body, "limitKey") + " = " + (long) decimal(body, "value")))
                .exceptionally(error -> {
                    message(player, "섬 제한을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandSnapshots(Player player, int limit) {
        currentIsland(player, "섬 안에서만 스냅샷을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandSnapshots(islandId, Math.max(1, Math.min(limit, 20)))
                .thenAccept(body -> message(player, snapshotListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 스냅샷을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void requestIslandSnapshot(Player player, String reason) {
        currentIsland(player, "섬 안에서만 스냅샷을 생성할 수 있습니다.").ifPresent(islandId -> {
            if (!player.isOp()) {
                player.sendMessage("섬 스냅샷을 생성할 관리자 권한이 없습니다.");
                return;
            }
            coreApiClient.requestIslandSnapshot(islandId, reason)
                .thenRun(() -> message(player, "섬 스냅샷 생성을 요청했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 스냅샷 생성을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private void restoreIslandSnapshot(Player player, long snapshotNo) {
        currentIsland(player, "섬 안에서만 스냅샷을 복원할 수 있습니다.").ifPresent(islandId -> {
            if (!player.isOp()) {
                player.sendMessage("섬 스냅샷을 복원할 관리자 권한이 없습니다.");
                return;
            }
            if (snapshotNo <= 0L) {
                player.sendMessage("올바른 스냅샷 번호를 입력해주세요.");
                return;
            }
            coreApiClient.restoreIslandSnapshot(islandId, snapshotNo)
                .thenRun(() -> message(player, "섬 스냅샷 복원을 요청했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 스냅샷 복원을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        java.util.Optional<UUID> islandId = protection.islandAt(player.getLocation().getBlock());
        if (islandId.isEmpty()) {
            player.sendMessage(missingMessage);
        }
        return islandId;
    }

    private boolean allowed(Player player, IslandPermission permission) {
        Location location = player.getLocation();
        return protection.checkBlock(
            player.getUniqueId(),
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            permission,
            player.isOp()
        ).allowed();
    }

    private boolean publicWarpAllowed(Player player, Point point, String islandInfo) {
        return point.publicAccess()
            && bool(islandInfo, "publicAccess")
            && protection.checkSystemFlag(player.getLocation().getBlock(), IslandFlag.PUBLIC_WARPS).allowed();
    }

    private IslandLocation location(Location location) {
        return new IslandLocation(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    private String pointListMessage(String body, String label, String emptyMessage) {
        List<String> names = names(body);
        return names.isEmpty() ? emptyMessage : label + ": " + String.join(", ", names);
    }

    private String rankingMessage(String body, String label, String valueKey) {
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
                String value = valueKey.equals("worth") ? text(object, valueKey) : Long.toString((long) decimal(object, valueKey));
                entries.add((entries.size() + 1) + ". " + islandId + " (" + value + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? label + ": 기록이 없습니다." : label + ": " + String.join(" | ", entries);
    }

    private String bankBalance(String body) {
        String balance = text(body, "balance");
        return balance.isBlank() ? "0" : balance;
    }

    private String upgradeListMessage(String body) {
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

    private String missionListMessage(String body, String label) {
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

    private String logListMessage(String body) {
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
            String action = text(object, "action");
            if (!action.isBlank()) {
                String actor = text(object, "actorUuid");
                entries.add(action + (actor.isBlank() ? "" : " by " + actor));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 로그가 없습니다." : "섬 로그: " + String.join(" | ", entries);
    }

    private String limitListMessage(String body) {
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
            String key = text(object, "limitKey");
            if (!key.isBlank()) {
                entries.add(key + "=" + (long) decimal(object, "value"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 제한이 없습니다." : "섬 제한: " + String.join(", ", entries);
    }

    private String snapshotListMessage(String body) {
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
            long snapshotNo = (long) decimal(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = text(object, "reason");
                entries.add("#" + snapshotNo + (reason.isBlank() ? "" : " " + reason));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 스냅샷이 없습니다." : "섬 스냅샷: " + String.join(", ", entries);
    }

    private String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Point point(String body, String requestedName, String fallbackWorldName) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String target = requestedName == null || requestedName.isBlank() ? "default" : requestedName;
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                return null;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                return null;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            if (target.equalsIgnoreCase(text(object, "name"))) {
                return new Point(
                    text(object, "worldName").isBlank() ? fallbackWorldName : text(object, "worldName"),
                    decimal(object, "localX"),
                    decimal(object, "localY"),
                    decimal(object, "localZ"),
                    (float) decimal(object, "yaw"),
                    (float) decimal(object, "pitch"),
                    bool(object, "publicAccess")
                );
            }
            index = objectEnd + 1;
        }
        return null;
    }

    private void teleport(Player player, Point point, String missingMessage, String successMessage) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (point == null) {
                player.sendMessage(missingMessage);
                return;
            }
            World world = plugin.getServer().getWorld(point.worldName());
            if (world == null) {
                player.sendMessage("대상 월드를 찾을 수 없습니다.");
                return;
            }
            player.teleport(new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch()));
            player.sendMessage(successMessage);
        });
    }

    private String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? "" : unescape(json.substring(valueStart, end));
    }

    private double decimal(String json, String key) {
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

    private boolean bool(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return false;
        }
        int valueStart = start + needle.length();
        return json.startsWith("true", valueStart);
    }

    private List<String> names(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        String needle = "\"name\":\"";
        int index = 0;
        while (index < body.length()) {
            int start = body.indexOf(needle, index);
            if (start < 0) {
                break;
            }
            int valueStart = start + needle.length();
            int end = jsonStringEnd(body, valueStart);
            if (end < 0) {
                break;
            }
            names.add(unescape(body.substring(valueStart, end)));
            index = end + 1;
        }
        return names;
    }

    private int jsonStringEnd(String value, int start) {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaped) {
                if (current == '\\') {
                    escaped = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                default -> builder.append(current);
            }
            escaped = false;
        }
        return builder.toString();
    }

    private void message(Player player, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }

    private record Point(String worldName, double x, double y, double z, float yaw, float pitch, boolean publicAccess) {}
}
