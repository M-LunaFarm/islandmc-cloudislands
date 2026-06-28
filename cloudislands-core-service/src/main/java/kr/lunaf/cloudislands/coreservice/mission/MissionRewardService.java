package kr.lunaf.cloudislands.coreservice.mission;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;

public final class MissionRewardService {
    private final IslandBankRepository bankRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandGeneratorRepository generatorRepository;
    private final IslandPermissionRuleRepository permissionRules;

    public MissionRewardService(
            IslandBankRepository bankRepository,
            IslandLimitRepository limitRepository,
            IslandGeneratorRepository generatorRepository,
            IslandPermissionRuleRepository permissionRules) {
        this.bankRepository = bankRepository;
        this.limitRepository = limitRepository;
        this.generatorRepository = generatorRepository;
        this.permissionRules = permissionRules;
    }

    public MissionRewardResult apply(IslandMissionSnapshot snapshot, UUID actorUuid) {
        if (snapshot == null || !snapshot.completed()) {
            return MissionRewardResult.skipped("MISSION_NOT_COMPLETED");
        }
        String rewardType = normalize(snapshot.rewardType());
        if (rewardType.isBlank()) {
            return MissionRewardResult.skipped("NO_REWARD");
        }
        return switch (rewardType) {
            case "BANK_DEPOSIT" -> bankDeposit(snapshot);
            case "COMMAND" -> queued("COMMAND_REWARD_QUEUED", "command", snapshot.reward());
            case "ITEM" -> queued("ITEM_REWARD_QUEUED", "item", snapshot.reward());
            case "UPGRADE_DISCOUNT" -> queued("UPGRADE_DISCOUNT_RECORDED", "discount", snapshot.reward());
            case "PERMISSION_TEMPORARY" -> permissionTemporary(snapshot, actorUuid);
            case "LIMIT_INCREASE" -> limitIncrease(snapshot, actorUuid);
            case "GENERATOR_TIER" -> generatorTier(snapshot);
            default -> MissionRewardResult.skipped("UNSUPPORTED_REWARD_" + rewardType);
        };
    }

    private MissionRewardResult bankDeposit(IslandMissionSnapshot snapshot) {
        if (bankRepository == null) {
            return MissionRewardResult.skipped("BANK_REPOSITORY_UNAVAILABLE");
        }
        Optional<BigDecimal> amount = bankDepositRewardAmount(snapshot);
        if (amount.isEmpty()) {
            return MissionRewardResult.skipped("INVALID_BANK_REWARD");
        }
        try {
            var deposited = bankRepository.deposit(snapshot.islandId(), amount.get());
            return new MissionRewardResult(true, "BANK_DEPOSITED", deposited.balance(), Map.of("amount", amount.get().toPlainString()));
        } catch (RuntimeException exception) {
            return MissionRewardResult.skipped("BANK_REWARD_FAILED");
        }
    }

    private MissionRewardResult permissionTemporary(IslandMissionSnapshot snapshot, UUID actorUuid) {
        if (permissionRules == null) {
            return queued("PERMISSION_TEMPORARY_RECORDED", "permission", snapshot.reward());
        }
        String permissionName = firstToken(snapshot.reward()).toUpperCase();
        try {
            IslandPermission permission = IslandPermission.valueOf(permissionName);
            permissionRules.putPlayerOverride(snapshot.islandId(), actorUuid, permission, true);
            return new MissionRewardResult(true, "PERMISSION_TEMPORARY_GRANTED", "", Map.of("permission", permission.name()));
        } catch (IllegalArgumentException exception) {
            return MissionRewardResult.skipped("INVALID_PERMISSION_REWARD");
        }
    }

    private MissionRewardResult limitIncrease(IslandMissionSnapshot snapshot, UUID actorUuid) {
        if (limitRepository == null) {
            return MissionRewardResult.skipped("LIMIT_REPOSITORY_UNAVAILABLE");
        }
        RewardKeyAmount parsed = rewardKeyAmount(snapshot.reward(), "MISSION");
        if (parsed.amount() <= 0L) {
            return MissionRewardResult.skipped("INVALID_LIMIT_REWARD");
        }
        long current = limitRepository.list(snapshot.islandId()).stream()
            .filter(limit -> limit.limitKey().equals(parsed.key()))
            .mapToLong(IslandLimitSnapshot::value)
            .findFirst()
            .orElse(0L);
        IslandLimitSnapshot updated = limitRepository.set(snapshot.islandId(), parsed.key(), current + parsed.amount(), actorUuid);
        return new MissionRewardResult(true, "LIMIT_INCREASED", "", Map.of(
            "limitKey", updated.limitKey(),
            "value", Long.toString(updated.value())
        ));
    }

    private MissionRewardResult generatorTier(IslandMissionSnapshot snapshot) {
        if (generatorRepository == null) {
            return MissionRewardResult.skipped("GENERATOR_REPOSITORY_UNAVAILABLE");
        }
        RewardKeyAmount parsed = rewardKeyAmount(snapshot.reward(), "default");
        if (parsed.amount() <= 0L) {
            return MissionRewardResult.skipped("INVALID_GENERATOR_TIER_REWARD");
        }
        IslandGeneratorSnapshot current = generatorRepository.profile(snapshot.islandId());
        String generatorKey = parsed.key().equals("DEFAULT") ? current.generatorKey() : parsed.key().toLowerCase();
        int nextLevel = Math.max(current.level(), Math.toIntExact(Math.min(Integer.MAX_VALUE, parsed.amount())));
        IslandGeneratorSnapshot updated = generatorRepository.setProfile(snapshot.islandId(), generatorKey, nextLevel);
        return new MissionRewardResult(true, "GENERATOR_TIER_SET", "", Map.of(
            "generatorKey", updated.generatorKey(),
            "level", Integer.toString(updated.level())
        ));
    }

    private static MissionRewardResult queued(String code, String detailKey, String reward) {
        String value = reward == null ? "" : reward.trim();
        if (value.isBlank()) {
            return MissionRewardResult.skipped("INVALID_" + detailKey.toUpperCase() + "_REWARD");
        }
        return new MissionRewardResult(true, code, "", Map.of(detailKey, value));
    }

    public static Optional<BigDecimal> bankDepositRewardAmount(IslandMissionSnapshot snapshot) {
        String reward = snapshot == null ? "" : snapshot.reward();
        StringBuilder amount = new StringBuilder();
        boolean started = false;
        for (int index = 0; index < reward.length(); index++) {
            char current = reward.charAt(index);
            if (Character.isDigit(current) || current == '.' || current == ',') {
                started = true;
                if (current != ',') {
                    amount.append(current);
                }
            } else if (started) {
                break;
            }
        }
        if (amount.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal parsed = new BigDecimal(amount.toString());
            return parsed.signum() > 0 ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static RewardKeyAmount rewardKeyAmount(String reward, String fallbackKey) {
        List<String> tokens = List.of((reward == null ? "" : reward.trim()).split("\\s+"));
        String key = tokens.isEmpty() || tokens.get(0).isBlank() ? fallbackKey : tokens.get(0);
        long amount = 0L;
        for (String token : tokens) {
            try {
                amount = Long.parseLong(token.replace("+", ""));
                break;
            } catch (NumberFormatException ignored) {
            }
        }
        return new RewardKeyAmount(key.toUpperCase(), amount);
    }

    private static String firstToken(String reward) {
        String value = reward == null ? "" : reward.trim();
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    private static String normalize(String rewardType) {
        return rewardType == null ? "" : rewardType.trim().toUpperCase();
    }

    private record RewardKeyAmount(String key, long amount) {}

    public record MissionRewardResult(boolean applied, String code, String balance, Map<String, String> details) {
        public MissionRewardResult {
            code = code == null ? "" : code;
            balance = balance == null ? "" : balance;
            details = details == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(details));
        }

        public static MissionRewardResult skipped(String code) {
            return new MissionRewardResult(false, code, "", Map.of());
        }
    }
}
