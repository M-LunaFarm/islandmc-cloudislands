private data class ReportMinecraftVersionRange(val major: Int, val minor: Int) : Comparable<ReportMinecraftVersionRange> {
    override fun compareTo(other: ReportMinecraftVersionRange): Int =
        compareValuesBy(this, other, ReportMinecraftVersionRange::major, ReportMinecraftVersionRange::minor)

    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)\.x""")

        fun parse(value: String): ReportMinecraftVersionRange {
            val match = PATTERN.matchEntire(value)
                ?: throw GradleException("Unsupported normalizedRange '$value'; expected '<major>.<minor>.x'")
            return ReportMinecraftVersionRange(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }
}

private data class ReportMinecraftVersionEntry(
    val id: String,
    val normalizedRange: String,
    val compileEnabled: Boolean,
    val bootSmokeEnabled: Boolean,
    val releaseSupported: Boolean,
    val experimental: Boolean,
    val adapterClass: String
) {
    val range: ReportMinecraftVersionRange = ReportMinecraftVersionRange.parse(normalizedRange)
    val taskSuffix: String = id.filter(Char::isDigit)
    val compileTaskName: String = "paper${taskSuffix}Compile"
    val bootSmokeTaskName: String = "paper${taskSuffix}BootSmoke"
    val adapterSimpleName: String = adapterClass.substringAfterLast('.')
}

private data class ReportMinecraftVersionMatrix(val entries: List<ReportMinecraftVersionEntry>) {
    companion object {
        fun parse(file: File): ReportMinecraftVersionMatrix {
            if (!file.isFile) {
                throw GradleException("Minecraft version matrix file is missing: ${file.path}")
            }
            val entries = mutableListOf<ReportMinecraftVersionEntry>()
            var current = linkedMapOf<String, String>()
            file.readLines().forEachIndexed { index, rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isBlank()) {
                    return@forEachIndexed
                }
                if (line == "[[versions]]") {
                    if (current.isNotEmpty()) {
                        entries.add(entry(current, file, index + 1))
                        current = linkedMapOf()
                    }
                    return@forEachIndexed
                }
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    throw GradleException("Invalid matrix line ${index + 1} in ${file.path}: $rawLine")
                }
                current[line.substring(0, separator).trim()] = scalar(line.substring(separator + 1).trim())
            }
            if (current.isNotEmpty()) {
                entries.add(entry(current, file, file.readLines().size))
            }
            return ReportMinecraftVersionMatrix(entries)
        }

        private fun entry(values: Map<String, String>, file: File, line: Int): ReportMinecraftVersionEntry =
            ReportMinecraftVersionEntry(
                id = required(values, "id", file, line),
                normalizedRange = required(values, "normalizedRange", file, line),
                compileEnabled = required(values, "compileEnabled", file, line).toBooleanStrict(),
                bootSmokeEnabled = required(values, "bootSmokeEnabled", file, line).toBooleanStrict(),
                releaseSupported = required(values, "releaseSupported", file, line).toBooleanStrict(),
                experimental = required(values, "experimental", file, line).toBooleanStrict(),
                adapterClass = required(values, "adapterClass", file, line)
            )

        private fun required(values: Map<String, String>, key: String, file: File, line: Int): String =
            values[key] ?: throw GradleException("Missing '$key' in Minecraft version matrix ${file.path} near line $line")

        private fun scalar(value: String): String =
            if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                value.substring(1, value.length - 1)
            } else {
                value
            }
    }
}

private data class FeatureParityEntry(
    val area: String,
    val status: String,
    val domain: String,
    val paperRuntime: String,
    val adapter: String,
    val integration: String,
    val recovery: String,
    val evidence: List<String>,
    val limitation: String
)

private data class PermissionParityEntry(
    val scope: String,
    val legacyNode: String,
    val cloudislandsNode: String,
    val status: String,
    val priority: String,
    val note: String
)

private data class PermissionBacklogItem(
    val priority: String,
    val scope: String,
    val legacyNode: String,
    val summary: String,
    val cloudislandsCoverage: String
)

private val superiorSkyblock2PermissionSourceUrls = listOf(
    "https://wiki.bg-software.com/superiorskyblock/overview/commands-and-permissions/player-commands",
    "https://wiki.bg-software.com/superiorskyblock/overview/commands-and-permissions/admin-commands",
    "https://wiki.bg-software.com/superiorskyblock/overview/commands-and-permissions/permissions"
)

private val superiorSkyblock2PlayerPermissions = listOf(
    "superior.chat.color",
    "superior.island.*",
    "superior.island.accept",
    "superior.island.balance",
    "superior.island.ban",
    "superior.island.bank",
    "superior.island.bans",
    "superior.island.biome",
    "superior.island.border",
    "superior.island.chest",
    "superior.island.close",
    "superior.island.coop",
    "superior.island.coops",
    "superior.island.counts",
    "superior.island.create",
    "superior.island.delwarp",
    "superior.island.demote",
    "superior.island.deposit",
    "superior.island.disband",
    "superior.island.expel",
    "superior.island.fly",
    "superior.island.help",
    "superior.island.invite",
    "superior.island.kick",
    "superior.island.lang",
    "superior.island.localchat",
    "superior.island.leave",
    "superior.island.members",
    "superior.island.mission",
    "superior.island.missions",
    "superior.island.name",
    "superior.island.open",
    "superior.island.panel",
    "superior.island.pardon",
    "superior.island.permissions",
    "superior.island.promote",
    "superior.island.rankup",
    "superior.island.rate",
    "superior.island.ratings",
    "superior.island.recalc",
    "superior.island.setdiscord",
    "superior.island.setpaypal",
    "superior.island.setrole",
    "superior.island.setteleport",
    "superior.island.settings",
    "superior.island.setwarp",
    "superior.island.show",
    "superior.island.stacker.*",
    "superior.island.stacker.<block-type>",
    "superior.island.team",
    "superior.island.teamchat",
    "superior.island.teleport",
    "superior.island.toggle",
    "superior.island.toggle.blocks",
    "superior.island.toggle.border",
    "superior.island.top",
    "superior.island.transfer",
    "superior.island.uncoop",
    "superior.island.upgrade",
    "superior.island.value",
    "superior.island.values",
    "superior.island.visit",
    "superior.island.visitors",
    "superior.island.warp",
    "superior.island.warps",
    "superior.island.withdraw"
)

private val superiorSkyblock2AdminPermissions = listOf(
    "superior.admin.*",
    "superior.admin.add",
    "superior.admin.addbanklimit",
    "superior.admin.addblocklimit",
    "superior.admin.addbonus",
    "superior.admin.addcooplimit",
    "superior.admin.addcropgrowth",
    "superior.admin.addeffect",
    "superior.admin.addentitylimit",
    "superior.admin.addgenerator",
    "superior.admin.addmobdrops",
    "superior.admin.addsize",
    "superior.admin.addspawnerrates",
    "superior.admin.addteamlimit",
    "superior.admin.addwarpslimit",
    "superior.admin.ban.bypass",
    "superior.admin.bonus",
    "superior.admin.bypass",
    "superior.admin.bypass.*",
    "superior.admin.bypass.cooldowns",
    "superior.admin.bypass.warmup",
    "superior.admin.chest",
    "superior.admin.cleargenerator",
    "superior.admin.close",
    "superior.admin.cmdall",
    "superior.admin.count",
    "superior.admin.data",
    "superior.admin.debug",
    "superior.admin.delwarp",
    "superior.admin.demote",
    "superior.admin.deposit",
    "superior.admin.disband",
    "superior.admin.fly",
    "superior.admin.givedisbands",
    "superior.admin.ignore",
    "superior.admin.join",
    "superior.admin.kick",
    "superior.admin.mission",
    "superior.admin.modules",
    "superior.admin.msg",
    "superior.admin.msgall",
    "superior.admin.name",
    "superior.admin.open",
    "superior.admin.openmenu",
    "superior.admin.promote",
    "superior.admin.purge",
    "superior.admin.rankup",
    "superior.admin.recalc",
    "superior.admin.reload",
    "superior.admin.removeblocklimit",
    "superior.admin.removeentitylimit",
    "superior.admin.removeratings",
    "superior.admin.resetpermissions",
    "superior.admin.resetsettings",
    "superior.admin.resetworld",
    "superior.admin.schematic",
    "superior.admin.setbanklimit",
    "superior.admin.setbiome",
    "superior.admin.setblockamount",
    "superior.admin.setblocklimit",
    "superior.admin.setchestrow",
    "superior.admin.setcooplimit",
    "superior.admin.setcropgrowth",
    "superior.admin.setdisbands",
    "superior.admin.seteffect",
    "superior.admin.setentitylimit",
    "superior.admin.setgenerator",
    "superior.admin.setislandpreview",
    "superior.admin.setleader",
    "superior.admin.setmobdrops",
    "superior.admin.setpermission",
    "superior.admin.setrate",
    "superior.admin.setrolelimit",
    "superior.admin.setsettings",
    "superior.admin.setsize",
    "superior.admin.setspawn",
    "superior.admin.setspawnerrates",
    "superior.admin.setteamlimit",
    "superior.admin.settings",
    "superior.admin.setupgrade",
    "superior.admin.setwarpslimit",
    "superior.admin.show",
    "superior.admin.spawn",
    "superior.admin.spy",
    "superior.admin.stats",
    "superior.admin.syncbonus",
    "superior.admin.syncupgrades",
    "superior.admin.teleport",
    "superior.admin.title",
    "superior.admin.titleall",
    "superior.admin.unignore",
    "superior.admin.withdraw",
    "superior.admin.world"
)

private fun featureParityEntries(): List<FeatureParityEntry> = listOf(
    FeatureParityEntry(
        "lifecycle/templates/homes/warps/visits",
        "IMPLEMENTED_VERIFIED",
        "Core lifecycle and route tickets are covered",
        "Paper 1.21.11 and 26.1.2 boot smoke load the plugin",
        "1.21.x and 26.1.x release adapters; 26.2 compile adapter",
        "ciIntegrationSmoke verifies cross-Core create, job, route, session, consume, and player-ticket cache convergence; Paper tests verify main-thread template permission preflight, stale target-info response rejection, scheduler-bound single-Paper fallback teleport, target-island coordinates, safe destination scans, and final bounded destination revalidation",
        "node-down recovery restore is covered by ciIntegrationSmoke",
        listOf(
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflowRestoreTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/platform/compatibility/Paper121FamilyAdapter.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/platform/world/SafeTeleportPolicyTest.java",
            "scripts/ci/core_integration_smoke.py"
        ),
        "26.1.2 is boot-verified; 26.2 stays compile-only until a stable Paper build is available"
    ),
    FeatureParityEntry(
        "access/bans/membership/roles/permissions",
        "IMPLEMENTED_VERIFIED",
        "Role IDs, permissions, bans, and member APIs have unit coverage",
        "Paper permission cache/listener paths compile under the adapter matrix",
        "Version-neutral domain with Paper adapter boundary tests",
        "Core API and permission event replay are exercised in tests",
        "replay and cache convergence are unit-tested; releaseClusterSmokeGate covers multi-Paper failover evidence",
        listOf(
            "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/permission/PermissionResolverTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutesTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"
        ),
        "third-party permission plugins are integration-status reported, not all boot-verified"
    ),
    FeatureParityEntry(
        "flags/protection",
        "IMPLEMENTED_VERIFIED",
        "Gameplay and permission flags are represented in the domain",
        "Paper protection listeners, granular painting, item-frame, and leash-knot permissions, extensible database permission-key guards, SS2-style natural and per-player time/weather flags, automation and growth boundaries, natural material transitions, dependent block breaks, and cache paths have unit coverage",
        "Bukkit-facing behavior is kept inside Paper runtime code",
        "unit verified; Paper policy tests cover granular interactions, durable role-gated personal flight with external-flight ownership isolation, durable per-player border visibility, real blue/green/red border color transitions, block-display preferences, transition refresh, and border ownership isolation, soft-explosion target authorization and non-destructive accounting, RoseStacker direct-spawn flag parity, default-compatible natural flags, shard-safe player time/weather overrides, automation and growth boundaries, natural spread, material transitions, dependent block breaks, raids, mob targeting, bounded asynchronous safe returns, and fail-closed player/entity cross-dimension portals inside active island regions",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/ProtectionControllerTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/PlayerFlightOwnershipPolicyTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/session/PlayerFlightPreferenceRegistryTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/PlayerIslandFlightService.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/platform/world/SafeTeleportPolicyTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/IslandPortalListener.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/IslandPortalListenerTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/IslandGameplayFlagListener.java",
            "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/permission/defaults/DefaultIslandPermissionsTest.java"
        ),
        "runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests; cross-dimension island worlds remain intentionally unavailable until their lifecycle, storage, and routing are implemented end to end"
    ),
    FeatureParityEntry(
        "ranking/level/worth/bank/block values",
        "IMPLEMENTED_VERIFIED",
        "ranking, dirty recalculation, authoritative bank balance ordering, typed block values, custom block identity, logical stack amounts, and tick-budgeted reconciliation have service tests",
        "Paper-facing values compile; no per-version runtime divergence is claimed",
        "version-neutral",
        "verifyRankingWorthCertification and verifyIntegrationRuntimeSmoke cover typed values, authoritative bank-balance ordering with ranking exclusions, custom block identity, RoseStacker/WildStacker/AdvancedSpawners logical amounts, cause-aware permanent entity removal, bounded scans, serialized writes, and concurrent-mutation rejection",
        "not recovery-specific",
        listOf(
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/DirtyRankingRecalculationTaskTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/LevelWorthSystemPolicyTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/bank/InMemoryIslandBankRepositoryTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/CustomBlockLevelAccountingPolicyTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/customitem/CustomBlockKeyServiceTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/IslandLevelScanServiceTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/IslandScanCursorTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/stacker/StackAmountServiceTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/StackAmountLevelAccountingPolicyTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/limit/LogicalStackDeltaBridge.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/limit/LogicalStackDeltaBridgeTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/limit/LogicalEntityRemovalBridge.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/limit/LogicalEntityRemovalBridgeTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/limit/MobDropRateScaler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/limit/MobDropRateScalerTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/ExplosionDeltaFinalityPolicyTest.java",
            "cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/hook/PlaceholderFeaturePolicyTest.java"
        ),
        "custom and stacker vendor APIs remain deployment-specific live acceptance; busy islands retry reconciliation instead of publishing a mixed-time scan"
    ),
    FeatureParityEntry(
        "upgrades/size/border/biome",
        "IMPLEMENTED_VERIFIED",
        "upgrade effects apply size, limits, fly, generator tier, biome validation, and player border policy with combined bank and warehouse-item prices",
        "Paper commands compile and tests cover command policy, authoritative border serialization, and scheduler-safe live border refresh",
        "Paper adapter isolates version-sensitive runtime access",
        "verifyUpgradeEffectCoverage covers Core upgrade effects, atomic multi-price charging/refunds, rule-complete GUI views, and biome normalization; authoritative size is carried through activation, restore, reset, and migration jobs, while live size changes atomically replace Paper protection, scan, and snapshot bounds; Core island response paths expose the independent authoritative BORDER limit, and async size, border, and border-policy events refresh every online player through the Paper scheduler with per-island burst deduplication; Paper tests also cover region-file cell isolation, unsafe-size fencing, world-border policy, activation-time persisted-biome reconciliation, and chunk-batched biome painting",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.java",
            "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandCatalogRoutes.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandCatalogRoutesTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutesTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/platform/compatibility/PaperPlatformBoundaryTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/upgrade/UpgradeEffectApplierTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutesTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/IslandBorderRuntimePolicyTest.java",
            "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/protection/RegionIndex.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/JobCompletionServiceTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/activation/IslandSizeRuntimeListener.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/activation/IslandSizeRuntimeListenerTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/activation/ShardCellGeometryPolicy.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/activation/ShardCellGeometryPolicyTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/platform/world/IslandBiomeRuntimeApplier.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/environment/IslandBiomeRuntimeApplierPolicyTest.java"
        ),
        "operator deployment acceptance is still recommended; cells below 1024 blocks or not aligned to 512 blocks fail startup, and islands that cannot fit without sharing region files fail activation or are fenced on unsafe live resize"
    ),
    FeatureParityEntry(
        "bank/economy/missions/challenges/generators/limits",
        "IMPLEMENTED_VERIFIED",
        "bank safety, conflict-safe upgrade charging with compensating refunds, economy hooks, mission triggers/rewards, challenges, generator rules, and limits have verification gates",
        "Paper mission listeners, bank rollback UX, and generator listeners have targeted tests",
        "version-neutral plus Paper/Satis runtime boundaries",
        "verifyMissionEventProgress covers final uncancelled block, farm, kill, fishing, capacity-bounded bulk crafting, enchanting, statistic, advancement, and item-consumption progress plus the bounded definition cache; reward-settlement tests cover failure reopening, repeatable reset, and durable warehouse item delivery; PostgreSQL/MySQL shared warehouse settlement records move through PREPARED and ESCROWED before Paper replays the exact mutation key, so reconnecting on another Paper node can resume protected deposits and withdrawals; `/is deposit *` and `/is withdraw *` resolve authoritative full balances through scheduler-safe Vault and Core queries before reusing the existing idempotent mutation, refund, and rollback paths; Paper warehouse policy rejects metadata-bearing items that its material-and-amount schema cannot restore, while overflow-safe logical-stack mob-drop scaling, upgrade CAS/refund, generator, and economy safety gates cover the remaining scope",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/IslandMissionProgressListener.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/CraftingMissionAmount.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/mission/CraftingMissionAmountTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/mission/IslandMissionProgressListenerPolicyTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/MissionDefinitionCache.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/mission/MissionDefinitionCacheTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/mission/MissionRewardServiceTest.java",
            "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/CoreIdempotencyExecutor.java",
            "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/JdbcCoreIdempotencyStore.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/CoreHttpRouteRegistrarTest.java",
            "cloudislands-core-client/src/test/java/kr/lunaf/cloudislands/coreclient/CoreMutationContextTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/WarehouseSettlement.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandWarehouseCommandHandler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/WarehouseSettlementTest.java",
            "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/warehouse/JdbcWarehouseSettlementRepository.java",
            "cloudislands-core-service/src/main/resources/db/migration/V82__warehouse_settlement_recovery.sql",
            "cloudislands-core-service/src/main/resources/db/mysql/V5__warehouse_settlement_recovery.sql",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/BankUseCaseTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/application/BankUseCase.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandBankCommandHandler.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandTabCompleter.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/WarehouseItemPolicy.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/generator/IslandGeneratorListener.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/limit/MobDropRateScaler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/limit/MobDropRateScalerTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/GeneratorRoutesTest.java"
        ),
        "brewing completion has no reliable Bukkit actor and is intentionally not guessed; operator live-server economy/provider acceptance is still recommended"
    ),
    FeatureParityEntry(
        "chat/logs/reviews",
        "IMPLEMENTED_VERIFIED",
        "chat listener, audit/log routes, visitor stats, and review moderation have verification gates",
        "Paper chat listener compiles under matrix and Core review moderation has route/repository/schema tests",
        "version-neutral where possible",
        "verifyReviewModerationCoverage plus current-visible-visitor classification, Core audit/visitor route tests, and LOWEST/HIGHEST mutually exclusive local/team-chat isolation cover current workflow",
        "audit log replay is covered in Core tests",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/session/TeamChatModeRegistryTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/CurrentIslandVisitorPolicy.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/CurrentIslandVisitorPolicyTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AuditRoutesTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutesTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/review/InMemoryIslandReviewRepositoryTest.java",
            "cloudislands-core-service/src/main/resources/db/migration/V70__review_moderation.sql"
        ),
        "live multi-player chat moderation acceptance is deployment-specific outside unit CI"
    ),
    FeatureParityEntry(
        "snapshots/rollback/migration/recovery",
        "IMPLEMENTED_VERIFIED",
        "bundle validation, extraction, migration, and rollback paths have tests",
        "Paper snapshot and restore hooks compile and 1.21.x boots",
        "bundle compatibility is checked before restore",
        "ciIntegrationSmoke verifies recovery restore with shared services",
        "node-down recovery and bundle compatibility are verified",
        listOf(
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/world/bundle/ExternalTarBundleExtractorTest.java",
            "cloudislands-migration/src/test/java/kr/lunaf/cloudislands/migration/rollback/MigrationRollbackServiceTest.java",
            "cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/ClusterSmokeVerifierTest.java"
        ),
        "releaseClusterSmokeGate now includes database backup, object bundle, manifest checksum, restore, route, and audit evidence"
    ),
    FeatureParityEntry(
        "Java API/events/addons",
        "IMPLEMENTED_VERIFIED",
        "public API, events, addon metadata, and compatibility contract are tested",
        "Paper API bridge has unit coverage",
        "version-neutral API with runtime metadata compatibility check",
        "apiCompatibilityCheck verifies release contract metadata and the public API signature baseline",
        "not recovery-specific",
        listOf(
            "cloudislands-testkit/src/main/java/kr/lunaf/cloudislands/testkit/ApiCompatibilityCheckCli.java",
            "cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/ApiContractVerifierTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AddonRoutesTest.java"
        ),
        "external addon certification depends on testkit evidence supplied by the addon"
    ),
    FeatureParityEntry(
        "integrations/localization/GUI",
        "PARTIAL_VERIFIED",
        "integration policy, localization files, GUI components, and scoped runtime certification fixtures exist",
        "Paper integration registry certifies Vault economy, PlaceholderAPI registration, Plan distributed analytics, vanish-safe player suggestions, custom block identity, and logical stack accounting while separating diagnostic-only state-transfer adapters",
        "plugin-specific operation adapters are active only where implemented",
        "verifyIntegrationRuntimeSmoke verifies executable runtime services including RoseStacker, WildStacker, and AdvancedSpawners logical amount reconciliation; Paper tests also verify formatting-only MiniMessage rendering with literal dynamic placeholders across branding, GUI, scoreboard, command, title, action-bar, boss-bar, kick, migration, routing, boundary, flag, and protection-notice components",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java",
            "cloudislands-paper/src/main/resources/config-v2/integrations.yml",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandConfirmationMenu.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/message/ConfiguredMessageComponents.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertification.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertificationTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/message/ConfiguredMessageComponentsTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/message/RichMessageRuntimeSurfaceTest.java"
        ),
        "Vault, PlaceholderAPI, Plan, vanish, custom-item, and stacker accounting services are executable; click, URL, insertion, selector, score, and NBT MiniMessage tags stay disabled; external lifecycle and state-transfer operations remain diagnostic until real executors exist"
    )
)

private fun permissionParity(
    scope: String,
    legacyNode: String,
    cloudislandsNode: String,
    status: String,
    priority: String,
    note: String
): PermissionParityEntry = PermissionParityEntry(scope, legacyNode, cloudislandsNode, status, priority, note)

private fun playerPermissionParityEntries(): List<PermissionParityEntry> = listOf(
    permissionParity("player", "superior.chat.color", "cloudislands.island.chat", "COVERED_BY", "", "island chat is implemented; rich text policy is controlled by CloudIslands localization/config"),
    permissionParity("player", "superior.island.*", "cloudislands.player", "COVERED_BY", "", "root player command grant maps to the CloudIslands player command grant"),
    permissionParity("player", "superior.island.accept", "cloudislands.island.invite.respond", "SUPPORTED_VERIFIED", "", "invite accept and decline are handled by the invite response permission"),
    permissionParity("player", "superior.island.balance", "cloudislands.island.bank", "SUPPORTED_VERIFIED", "", "bank balance routes and commands are covered by the bank permission"),
    permissionParity("player", "superior.island.ban", "cloudislands.island.kick", "COVERED_BY", "", "member removal, bans, pardons, and visitor expel share the membership enforcement permission"),
    permissionParity("player", "superior.island.bank", "cloudislands.island.bank", "SUPPORTED_VERIFIED", "", "bank menu access is represented directly"),
    permissionParity("player", "superior.island.bans", "cloudislands.island.members", "COVERED_BY", "", "ban list access is part of the member management surface"),
    permissionParity("player", "superior.island.biome", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "biome changes are grouped with environment controls"),
    permissionParity("player", "superior.island.border", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "border size, visibility, and color use the environment permission"),
    permissionParity("player", "superior.island.chest", "cloudislands.island.warehouse.view", "SUPPORTED_VERIFIED", "", "island chest maps to CloudIslands warehouse view"),
    permissionParity("player", "superior.island.close", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "public/private and lock state are CloudIslands settings"),
    permissionParity("player", "superior.island.coop", "cloudislands.island.trust", "SUPPORTED_VERIFIED", "P1", "trust/coop creates the dedicated TRUSTED co-op role with an independent role limit and rejects permanent team members"),
    permissionParity("player", "superior.island.coops", "cloudislands.island.members", "SUPPORTED_VERIFIED", "P1", "legacy coops is translated to the member and co-op management surface"),
    permissionParity("player", "superior.island.counts", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "block counts are in the progression/value command group"),
    permissionParity("player", "superior.island.create", "cloudislands.island.create", "SUPPORTED_VERIFIED", "", "island creation maps directly"),
    permissionParity("player", "superior.island.delwarp", "cloudislands.island.setwarp", "SUPPORTED_VERIFIED", "", "warp creation, publication, and deletion share the setwarp grant"),
    permissionParity("player", "superior.island.demote", "cloudislands.island.demote", "SUPPORTED_VERIFIED", "", "member demotion maps directly"),
    permissionParity("player", "superior.island.deposit", "cloudislands.island.bank.deposit", "SUPPORTED_VERIFIED", "", "bank deposit maps directly"),
    permissionParity("player", "superior.island.disband", "cloudislands.island.delete", "COVERED_BY", "", "permanent island disband maps to the guarded delete/reset flow"),
    permissionParity("player", "superior.island.expel", "cloudislands.island.kick", "COVERED_BY", "", "visitor expel is grouped with membership enforcement"),
    permissionParity("player", "superior.island.fly", "cloudislands.island.fly", "SUPPORTED_VERIFIED", "", "personal flight preference is Core-persisted, role-gated, island-flag-gated, and revokes only CloudIslands-owned flight"),
    permissionParity("player", "superior.island.help", "cloudislands.island.menu", "SUPPORTED_VERIFIED", "", "help and command listing are menu-safe commands"),
    permissionParity("player", "superior.island.invite", "cloudislands.island.invite", "SUPPORTED_VERIFIED", "", "member invite maps directly"),
    permissionParity("player", "superior.island.kick", "cloudislands.island.kick", "SUPPORTED_VERIFIED", "", "member kick maps directly"),
    permissionParity("player", "superior.island.lang", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "language selection is in settings"),
    permissionParity("player", "superior.island.leave", "cloudislands.island.leave", "SUPPORTED_VERIFIED", "", "leave maps directly"),
    permissionParity("player", "superior.island.members", "cloudislands.island.members", "SUPPORTED_VERIFIED", "", "member panel maps directly"),
    permissionParity("player", "superior.island.mission", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "mission completion is part of progression"),
    permissionParity("player", "superior.island.missions", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "mission/challenge menus are part of progression"),
    permissionParity("player", "superior.island.localchat", "cloudislands.island.chat", "SUPPORTED_VERIFIED", "", "island-local chat mode and direct messages map to the shared chat channel"),
    permissionParity("player", "superior.island.name", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "island rename is a settings command"),
    permissionParity("player", "superior.island.open", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "public/private visibility is a settings command"),
    permissionParity("player", "superior.island.panel", "cloudislands.island.menu", "COVERED_BY", "", "panel access maps to the CloudIslands menu surface"),
    permissionParity("player", "superior.island.pardon", "cloudislands.island.kick", "COVERED_BY", "", "unban/pardon is grouped with membership enforcement"),
    permissionParity("player", "superior.island.permissions", "cloudislands.island.permissions", "SUPPORTED_VERIFIED", "", "role permissions and exceptions map directly"),
    permissionParity("player", "superior.island.promote", "cloudislands.island.promote", "SUPPORTED_VERIFIED", "", "promotion maps directly"),
    permissionParity("player", "superior.island.rankup", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "upgrade purchase/rankup is part of progression"),
    permissionParity("player", "superior.island.rate", "cloudislands.island.review", "SUPPORTED_VERIFIED", "", "rating submission is part of review moderation"),
    permissionParity("player", "superior.island.ratings", "cloudislands.island.review", "SUPPORTED_VERIFIED", "", "ratings/reviews listing is part of review moderation"),
    permissionParity("player", "superior.island.recalc", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "level/worth recalculation is part of progression"),
    permissionParity("player", "superior.island.setdiscord", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "P2", "island Discord metadata is exposed by /is setdiscord and persisted through typed Core island flags with audit/event coverage"),
    permissionParity("player", "superior.island.setpaypal", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "P2", "island PayPal metadata is exposed by /is setpaypal and persisted through typed Core island flags with audit/event coverage"),
    permissionParity("player", "superior.island.setrole", "cloudislands.island.promote", "COVERED_BY", "", "role assignment/edit is grouped with promotion and role editing"),
    permissionParity("player", "superior.island.setteleport", "cloudislands.island.sethome", "SUPPORTED_VERIFIED", "", "teleport point setup maps to sethome"),
    permissionParity("player", "superior.island.settings", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "settings menu maps directly"),
    permissionParity("player", "superior.island.setwarp", "cloudislands.island.setwarp", "SUPPORTED_VERIFIED", "", "warp creation maps directly"),
    permissionParity("player", "superior.island.show", "cloudislands.island.menu", "COVERED_BY", "", "island info/show is available through the overview/menu permission"),
    permissionParity("player", "superior.island.stacker.*", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "P2", "stacked block visibility is exposed by /is toggle blocks and addon stacker adapters enforce effective entity/spawner count state through Core-backed integration hooks"),
    permissionParity("player", "superior.island.stacker.<block-type>", "cloudislands.island.environment", "COVERED_BY", "P2", "per-block stacker grants collapse to CloudIslands environment permission plus block amount limit keys and stacker state export/restore safety barriers"),
    permissionParity("player", "superior.island.team", "cloudislands.island.members", "COVERED_BY", "", "team listing maps to member management"),
    permissionParity("player", "superior.island.teamchat", "cloudislands.island.chat", "SUPPORTED_VERIFIED", "", "team chat maps directly"),
    permissionParity("player", "superior.island.teleport", "cloudislands.island.home", "SUPPORTED_VERIFIED", "", "island teleport maps to home access"),
    permissionParity("player", "superior.island.toggle", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "toggle commands are grouped with environment controls"),
    permissionParity("player", "superior.island.toggle.blocks", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "block count toggles are grouped with environment controls"),
    permissionParity("player", "superior.island.toggle.border", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "border display toggle is grouped with environment controls"),
    permissionParity("player", "superior.island.top", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "top island views are part of progression/ranking"),
    permissionParity("player", "superior.island.transfer", "cloudislands.island.transfer", "SUPPORTED_VERIFIED", "", "ownership transfer maps directly"),
    permissionParity("player", "superior.island.uncoop", "cloudislands.island.kick", "SUPPORTED_VERIFIED", "P1", "legacy uncoop is translated to member removal so temporary cooperation cannot become permanent membership"),
    permissionParity("player", "superior.island.upgrade", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "upgrade menu and purchase are part of progression"),
    permissionParity("player", "superior.island.value", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "worth/value lookup is part of progression"),
    permissionParity("player", "superior.island.values", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "block values lookup is part of progression"),
    permissionParity("player", "superior.island.visit", "cloudislands.island.warp", "SUPPORTED_VERIFIED", "", "public island visit maps to visit/warp access"),
    permissionParity("player", "superior.island.visitors", "cloudislands.island.visitor-stats", "SUPPORTED_VERIFIED", "", "visitors lists currently present visible guests and temporary co-ops while visitor-stats keeps historical totals and recent visits"),
    permissionParity("player", "superior.island.warp", "cloudislands.island.warp", "SUPPORTED_VERIFIED", "", "warp travel maps directly"),
    permissionParity("player", "superior.island.warps", "cloudislands.island.warp", "SUPPORTED_VERIFIED", "", "warp list maps directly"),
    permissionParity("player", "superior.island.withdraw", "cloudislands.island.bank.withdraw", "SUPPORTED_VERIFIED", "", "bank withdrawal maps directly")
)

private fun adminPermissionParityEntries(): List<PermissionParityEntry> = listOf(
    permissionParity("admin", "superior.admin.*", "cloudislands.admin", "COVERED_BY", "", "root admin grant maps to the CloudIslands admin root"),
    permissionParity("admin", "superior.admin.add", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member add is exposed by /ciadmin island member add with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addbanklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive bank-limit mutation is exposed by /ciadmin island addbanklimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addblocklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive block-limit mutation is exposed by /ciadmin island addblocklimit using shared BLOCK_AMOUNT limit keys"),
    permissionParity("admin", "superior.admin.addbonus", "cloudislands.admin.upgrade-rules", "SUPPORTED_VERIFIED", "P2", "direct legacy bonus compatibility mutation is exposed by /ciadmin addbonus using Core BONUS:<key> limit state"),
    permissionParity("admin", "superior.admin.addcooplimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "co-op capacity additive mutation is exposed through the enforced TRUSTED role limit key"),
    permissionParity("admin", "superior.admin.addcropgrowth", "cloudislands.admin.setcropgrowth", "COVERED_BY", "", "CloudIslands exposes absolute crop-growth mutation"),
    permissionParity("admin", "superior.admin.addeffect", "cloudislands.admin.seteffect", "COVERED_BY", "", "CloudIslands exposes absolute effect mutation"),
    permissionParity("admin", "superior.admin.addentitylimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive entity-limit mutation is exposed by /ciadmin island addentitylimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addgenerator", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive generator mutation is exposed by /ciadmin island addgenerator with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addmobdrops", "cloudislands.admin.setmobdrops", "COVERED_BY", "", "CloudIslands exposes absolute mob-drop mutation"),
    permissionParity("admin", "superior.admin.addsize", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive island-size mutation is exposed by /ciadmin island addsize with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addspawnerrates", "cloudislands.admin.setspawnerrates", "COVERED_BY", "", "CloudIslands exposes absolute spawner-rate mutation"),
    permissionParity("admin", "superior.admin.addteamlimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive team-limit mutation is exposed by /ciadmin island addteamlimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addwarpslimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive warp-limit mutation is exposed by /ciadmin island addwarpslimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.ban.bypass", "cloudislands.admin.bypass", "COVERED_BY", "", "admin bypass covers moderation and protection bypass semantics"),
    permissionParity("admin", "superior.admin.bonus", "cloudislands.admin.upgrade-rules", "SUPPORTED_VERIFIED", "P2", "legacy bonus inspection is exposed by /ciadmin bonus over Core BONUS:<key> limit state"),
    permissionParity("admin", "superior.admin.bypass", "cloudislands.admin.bypass", "SUPPORTED_VERIFIED", "", "admin bypass maps directly"),
    permissionParity("admin", "superior.admin.bypass.*", "cloudislands.admin.bypass", "COVERED_BY", "", "specific bypass grants collapse to the CloudIslands bypass root"),
    permissionParity("admin", "superior.admin.bypass.cooldowns", "cloudislands.bypass.cooldown", "SUPPORTED_VERIFIED", "", "cooldown bypass maps directly"),
    permissionParity("admin", "superior.admin.bypass.warmup", "cloudislands.bypass.warmup", "SUPPORTED_VERIFIED", "", "warmup bypass maps directly"),
    permissionParity("admin", "superior.admin.chest", "cloudislands.admin.storage", "COVERED_BY", "", "storage inspection is represented by admin storage commands"),
    permissionParity("admin", "superior.admin.cleargenerator", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct generator clearing is exposed by /ciadmin island cleargenerator with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.close", "cloudislands.admin.island", "COVERED_BY", "", "island activation and lifecycle controls are admin island commands"),
    permissionParity("admin", "superior.admin.cmdall", "cloudislands.admin.cmd", "SUPPORTED_VERIFIED", "P1", "guarded /ciadmin cmd player/island/all dispatch is disabled by default and requires explicit cmd permission plus --confirm"),
    permissionParity("admin", "superior.admin.count", "cloudislands.admin.block-values", "COVERED_BY", "", "block value/count inspection is covered by block-value and island info surfaces"),
    permissionParity("admin", "superior.admin.data", "cloudislands.admin.support-bundle", "COVERED_BY", "", "diagnostic data export maps to support bundles"),
    permissionParity("admin", "superior.admin.debug", "cloudislands.admin.diagnostics", "COVERED_BY", "", "debug output maps to diagnostics/export commands"),
    permissionParity("admin", "superior.admin.delwarp", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin warp deletion is exposed by /ciadmin island delwarp with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.demote", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member demotion is exposed by /ciadmin island member demote with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.deposit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin bank deposit is exposed by /ciadmin island bank deposit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.disband", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "", "guarded island delete/repair/recover commands cover destructive lifecycle control"),
    permissionParity("admin", "superior.admin.fly", "cloudislands.admin.fly", "SUPPORTED_VERIFIED", "P2", "direct admin fly toggle is exposed by /ciadmin fly player/island/all with typed Core island-member resolution and Paper flight-state mutation"),
    permissionParity("admin", "superior.admin.givedisbands", "cloudislands.admin.player", "SUPPORTED_VERIFIED", "P2", "direct additive player disband quota mutation is exposed by /ciadmin player givedisbands with typed Core profile persistence and audit coverage"),
    permissionParity("admin", "superior.admin.ignore", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "ranking ignore state is exposed by /ciadmin island ignore with typed Core audit and cache-invalidation coverage"),
    permissionParity("admin", "superior.admin.join", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "forced admin self-join is exposed by /ciadmin island join with typed Core member-add audit and event coverage"),
    permissionParity("admin", "superior.admin.kick", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member kick is exposed by /ciadmin island member kick with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.mission", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct mission completion/progress mutation is exposed by /ciadmin island mission with typed Core audit, event, and reward coverage"),
    permissionParity("admin", "superior.admin.modules", "cloudislands.admin.addons", "COVERED_BY", "", "module/addon status maps to addon administration"),
    permissionParity("admin", "superior.admin.msg", "cloudislands.admin.message", "SUPPORTED_VERIFIED", "P1", "/ciadmin message player/island resolves typed Core island members and sends to online Paper recipients"),
    permissionParity("admin", "superior.admin.msgall", "cloudislands.admin.message", "SUPPORTED_VERIFIED", "P1", "/ciadmin message all sends to online Paper recipients with admin runtime audit logging"),
    permissionParity("admin", "superior.admin.name", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin island rename is exposed by /ciadmin island rename with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.open", "cloudislands.admin.island", "COVERED_BY", "", "island activation/lifecycle commands cover availability control"),
    permissionParity("admin", "superior.admin.openmenu", "cloudislands.admin.openmenu", "SUPPORTED_VERIFIED", "P2", "remote menu opening is exposed by /ciadmin openmenu <player> <menuId> with a fixed supported-menu allowlist and audit logging"),
    permissionParity("admin", "superior.admin.promote", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member promotion is exposed by /ciadmin island member promote with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.purge", "cloudislands.admin.storage", "COVERED_BY", "", "storage and recovery tooling covers destructive data maintenance paths"),
    permissionParity("admin", "superior.admin.rankup", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin upgrade purchase/rankup is exposed by /ciadmin island rankup through the typed admin progression client"),
    permissionParity("admin", "superior.admin.recalc", "cloudislands.admin.rankings", "COVERED_BY", "", "ranking recalculation and inspection map to ranking administration"),
    permissionParity("admin", "superior.admin.reload", "cloudislands.admin.reload", "SUPPORTED_VERIFIED", "", "reload maps directly"),
    permissionParity("admin", "superior.admin.removeblocklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct block-limit removal is exposed by /ciadmin island removeblocklimit using shared BLOCK_AMOUNT limit keys"),
    permissionParity("admin", "superior.admin.removeentitylimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct entity-limit removal is exposed by /ciadmin island removeentitylimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.removeratings", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "reviewer-specific rating removal is exposed by /ciadmin island removeratings through typed review deletion"),
    permissionParity("admin", "superior.admin.resetpermissions", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct role permission reset is exposed by /ciadmin island resetpermissions with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.resetsettings", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct island flag reset is exposed by /ciadmin island resetsettings with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.resetworld", "cloudislands.admin.island", "COVERED_BY", "", "snapshot restore/reset workflow covers world reset recovery"),
    permissionParity("admin", "superior.admin.schematic", "cloudislands.admin.templates", "COVERED_BY", "", "schematic/template management maps to template administration"),
    permissionParity("admin", "superior.admin.setbanklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct bank-limit mutation is exposed by /ciadmin island setbanklimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setbiome", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin biome mutation is exposed by /ciadmin island setbiome with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setblockamount", "cloudislands.admin.setblockamount", "SUPPORTED_VERIFIED", "", "block amount mutation maps directly"),
    permissionParity("admin", "superior.admin.setblocklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct block-limit mutation is exposed by /ciadmin island setblocklimit using shared BLOCK_AMOUNT limit keys"),
    permissionParity("admin", "superior.admin.setchestrow", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct warehouse row mutation is exposed by /ciadmin island setchestrow through the WAREHOUSE_ROWS limit consumed by the warehouse menu"),
    permissionParity("admin", "superior.admin.setcooplimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "co-op capacity mutation is exposed through the enforced TRUSTED role limit key"),
    permissionParity("admin", "superior.admin.setcropgrowth", "cloudislands.admin.setcropgrowth", "SUPPORTED_VERIFIED", "", "crop growth mutation maps directly"),
    permissionParity("admin", "superior.admin.setdisbands", "cloudislands.admin.player", "SUPPORTED_VERIFIED", "P2", "direct player disband quota mutation is exposed by /ciadmin player setdisbands with typed Core profile persistence and audit coverage"),
    permissionParity("admin", "superior.admin.seteffect", "cloudislands.admin.seteffect", "SUPPORTED_VERIFIED", "", "effect mutation maps directly"),
    permissionParity("admin", "superior.admin.setentitylimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct entity-limit mutation is exposed by /ciadmin island setentitylimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setgenerator", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct generator mutation is exposed by /ciadmin island setgenerator with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setislandpreview", "cloudislands.admin.templates", "COVERED_BY", "", "template preview management maps to template administration"),
    permissionParity("admin", "superior.admin.setleader", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin leader reassignment is exposed by /ciadmin island member setleader with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setmobdrops", "cloudislands.admin.setmobdrops", "SUPPORTED_VERIFIED", "", "mob drop mutation maps directly"),
    permissionParity("admin", "superior.admin.setpermission", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin permission mutation is exposed by /ciadmin island setpermission with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setrate", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct reviewer rating mutation is exposed by /ciadmin island setrate through typed review mutation"),
    permissionParity("admin", "superior.admin.setrolelimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct role-limit mutation is exposed by /ciadmin island setrolelimit through the enforced ROLE_LIMIT:<role> limit key"),
    permissionParity("admin", "superior.admin.setsettings", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct island flag mutation is exposed by /ciadmin island setsettings with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setsize", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct island size mutation is exposed by /ciadmin island setsize with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setspawn", "cloudislands.admin.setspawn", "SUPPORTED_VERIFIED", "P2", "server spawn mutation is exposed by /ciadmin setspawn using Paper World#setSpawnLocation with audit logging"),
    permissionParity("admin", "superior.admin.setspawnerrates", "cloudislands.admin.setspawnerrates", "SUPPORTED_VERIFIED", "", "spawner rate mutation maps directly"),
    permissionParity("admin", "superior.admin.setteamlimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct team-limit mutation is exposed by /ciadmin island setteamlimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.settings", "cloudislands.admin.config", "COVERED_BY", "", "in-game config editor maps to config administration"),
    permissionParity("admin", "superior.admin.setupgrade", "cloudislands.admin.upgrade-rules", "COVERED_BY", "", "upgrade rule mutation maps to upgrade-rules administration"),
    permissionParity("admin", "superior.admin.setwarpslimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct warp-limit mutation is exposed by /ciadmin island setwarpslimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.show", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "", "island info maps to admin island commands"),
    permissionParity("admin", "superior.admin.spawn", "cloudislands.admin.node", "COVERED_BY", "", "node/world routing covers operational spawn context"),
    permissionParity("admin", "superior.admin.spy", "cloudislands.admin.spy", "SUPPORTED_VERIFIED", "P2", "admin chat spy is exposed by /ciadmin spy with Paper global chat and Core-backed island/team chat delivery plus audit logging"),
    permissionParity("admin", "superior.admin.stats", "cloudislands.admin.metrics", "SUPPORTED_VERIFIED", "", "stats maps to metrics"),
    permissionParity("admin", "superior.admin.syncbonus", "cloudislands.admin.upgrade-rules", "SUPPORTED_VERIFIED", "P2", "legacy bonus synchronization is exposed by /ciadmin syncbonus through admin upgrade-effect recalculation"),
    permissionParity("admin", "superior.admin.syncupgrades", "cloudislands.admin.upgrade-rules", "COVERED_BY", "", "upgrade synchronization maps to upgrade rule administration"),
    permissionParity("admin", "superior.admin.teleport", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "", "admin island teleport maps to admin island commands"),
    permissionParity("admin", "superior.admin.title", "cloudislands.admin.title", "SUPPORTED_VERIFIED", "P1", "/ciadmin title player/island resolves typed Core island members and shows Adventure titles to online recipients"),
    permissionParity("admin", "superior.admin.titleall", "cloudislands.admin.title", "SUPPORTED_VERIFIED", "P1", "/ciadmin title all shows Adventure titles to online Paper recipients with admin runtime audit logging"),
    permissionParity("admin", "superior.admin.unignore", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "ranking unignore state is exposed by /ciadmin island unignore with typed Core audit and cache-invalidation coverage"),
    permissionParity("admin", "superior.admin.withdraw", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin bank withdrawal is exposed by /ciadmin island bank withdraw with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.world", "cloudislands.admin.storage", "COVERED_BY", "", "world/storage verification maps to admin storage commands")
)

private fun superiorSkyblock2PermissionParityEntries(): List<PermissionParityEntry> =
    playerPermissionParityEntries() + adminPermissionParityEntries()

private fun superiorSkyblock2PermissionBacklog(): List<PermissionBacklogItem> =
    superiorSkyblock2PermissionParityEntries()
        .filter { it.status !in setOf("SUPPORTED_VERIFIED", "COVERED_BY") }
        .filter { it.priority in setOf("P0", "P1", "P2") }
        .map { entry ->
            PermissionBacklogItem(
                entry.priority,
                entry.scope,
                entry.legacyNode,
                "Implement compatibility for ${entry.legacyNode}",
                entry.cloudislandsNode
            )
        }
        .sortedWith(compareBy<PermissionBacklogItem> { it.priority }.thenBy { it.scope }.thenBy { it.legacyNode })

private fun verifySuperiorSkyblock2PermissionParityMatrix() {
    val entries = superiorSkyblock2PermissionParityEntries()
    val duplicateNodes = entries.groupBy { it.scope to it.legacyNode }.filterValues { it.size > 1 }.keys
    if (duplicateNodes.isNotEmpty()) {
        throw GradleException("Duplicate SuperiorSkyblock2 permission mappings: ${duplicateNodes.joinToString()}")
    }
    val playerMapped = entries.filter { it.scope == "player" }.map { it.legacyNode }.toSet()
    val adminMapped = entries.filter { it.scope == "admin" }.map { it.legacyNode }.toSet()
    val playerExpected = superiorSkyblock2PlayerPermissions.toSet()
    val adminExpected = superiorSkyblock2AdminPermissions.toSet()
    if (playerMapped != playerExpected) {
        throw GradleException("SuperiorSkyblock2 player permission matrix drift; missing=${(playerExpected - playerMapped).joinToString()} extra=${(playerMapped - playerExpected).joinToString()}")
    }
    if (adminMapped != adminExpected) {
        throw GradleException("SuperiorSkyblock2 admin permission matrix drift; missing=${(adminExpected - adminMapped).joinToString()} extra=${(adminMapped - adminExpected).joinToString()}")
    }
    val invalidStatuses = entries.filterNot { it.status in setOf("SUPPORTED_VERIFIED", "COVERED_BY", "PLANNED", "INTENTIONALLY_UNSUPPORTED") }
    if (invalidStatuses.isNotEmpty()) {
        throw GradleException("Unsupported SuperiorSkyblock2 permission parity status values: ${invalidStatuses.map { it.legacyNode + "=" + it.status }.joinToString()}")
    }
    val invalidPriorities = entries.filterNot { it.priority.isBlank() || it.priority in setOf("P0", "P1", "P2") }
    if (invalidPriorities.isNotEmpty()) {
        throw GradleException("Unsupported SuperiorSkyblock2 permission backlog priorities: ${invalidPriorities.map { it.legacyNode + "=" + it.priority }.joinToString()}")
    }
    val incompleteHighPriority = entries.filter {
        it.priority in setOf("P0", "P1") && it.status !in setOf("SUPPORTED_VERIFIED", "COVERED_BY")
    }
    if (incompleteHighPriority.isNotEmpty()) {
        throw GradleException("SuperiorSkyblock2 P0/P1 permission parity must be supported; incomplete=${incompleteHighPriority.map { it.legacyNode + "=" + it.status }.joinToString()}")
    }
    val backlogPriorities = superiorSkyblock2PermissionBacklog().map { it.priority }.toSet()
    val expectedBacklogPriorities = entries
        .filter { it.status !in setOf("SUPPORTED_VERIFIED", "COVERED_BY") }
        .map { it.priority }
        .filter { it in setOf("P0", "P1", "P2") }
        .toSet()
    if (backlogPriorities != expectedBacklogPriorities) {
        throw GradleException("SuperiorSkyblock2 permission backlog priority drift; expected=${expectedBacklogPriorities.joinToString()} actual=${backlogPriorities.joinToString()}")
    }
}

private fun featureParityReadmeBlock(): String {
    val rows = featureParityEntries().joinToString("\n") { entry ->
        "| ${entry.area} | ${entry.status} | ${entry.integration} | ${entry.limitation} |"
    }
    return listOf(
        "<!-- feature-parity:start -->",
        "| Area | Status | Verified evidence | Limit |",
        "|---|---|---|---|",
        rows,
        "<!-- feature-parity:end -->"
    ).joinToString("\n")
}

private fun featureParityMarkdown(): String = buildString {
    appendLine("# SuperiorSkyblock2 parity")
    appendLine()
    appendLine("Status values: IMPLEMENTED_VERIFIED, IMPLEMENTED_UNVERIFIED, PARTIAL, PLANNED, NOT_APPLICABLE, INTENTIONALLY_UNSUPPORTED.")
    appendLine()
    appendLine("| Area | Status | Domain | Paper runtime | Adapter | Integration | Recovery | Evidence | Limit |")
    appendLine("|---|---|---|---|---|---|---|---|---|")
    featureParityEntries().forEach { entry ->
        appendLine("| ${entry.area} | ${entry.status} | ${entry.domain} | ${entry.paperRuntime} | ${entry.adapter} | ${entry.integration} | ${entry.recovery} | ${entry.evidence.joinToString("<br>") { "`$it`" }} | ${entry.limitation} |")
    }
    appendLine()
    appendLine("## SuperiorSkyblock2 permission parity")
    appendLine()
    appendLine("Source pages:")
    superiorSkyblock2PermissionSourceUrls.forEach { url ->
        appendLine("- $url")
    }
    appendLine()
    appendLine("Permission status values: SUPPORTED_VERIFIED, COVERED_BY, PLANNED, INTENTIONALLY_UNSUPPORTED.")
    appendLine()
    appendLine("### Player permissions")
    appendLine()
    appendLine("| SuperiorSkyblock2 permission | CloudIslands coverage | Status | Backlog | Notes |")
    appendLine("|---|---|---|---|---|")
    playerPermissionParityEntries().forEach { entry ->
        appendLine("| `${entry.legacyNode}` | `${entry.cloudislandsNode}` | ${entry.status} | ${entry.priority.ifBlank { "shipped" }} | ${entry.note} |")
    }
    appendLine()
    appendLine("### Admin permissions")
    appendLine()
    appendLine("| SuperiorSkyblock2 permission | CloudIslands coverage | Status | Backlog | Notes |")
    appendLine("|---|---|---|---|---|")
    adminPermissionParityEntries().forEach { entry ->
        appendLine("| `${entry.legacyNode}` | `${entry.cloudislandsNode}` | ${entry.status} | ${entry.priority.ifBlank { "shipped" }} | ${entry.note} |")
    }
    appendLine()
    appendLine("### Generated backlog")
    setOf("P0", "P1", "P2").forEach { priority ->
        appendLine()
        appendLine("#### $priority")
        superiorSkyblock2PermissionBacklog().filter { it.priority == priority }.forEach { item ->
            appendLine("- `${item.legacyNode}` (${item.scope}) -> `${item.cloudislandsCoverage}`: ${item.summary}")
        }
    }
}

private fun minecraftVersionFeatureMatrixMarkdown(matrix: ReportMinecraftVersionMatrix): String = buildString {
    appendLine("# Minecraft version feature matrix")
    appendLine()
    appendLine("| Version | Feature area | Domain | Paper runtime | Adapter | Compile | Boot | Integration | Recovery | Evidence |")
    appendLine("|---|---|---|---|---|---|---|---|---|---|")
    matrix.entries.sortedBy { it.range }.forEach { version ->
        featureParityEntries().forEach { entry ->
            val compile = if (version.compileEnabled) "verified by `${version.compileTaskName}`" else "not verified"
            val boot = if (version.bootSmokeEnabled) "verified by `${version.bootSmokeTaskName}`" else "pending official Paper build"
            val integration = if (version.releaseSupported && !version.experimental) entry.integration else "compile-only adapter coverage; no boot or integration claim"
            appendLine("| ${version.normalizedRange} | ${entry.area} | ${entry.domain} | ${entry.paperRuntime} | ${entry.adapter} | $compile | $boot | $integration | ${entry.recovery} | ${entry.evidence.joinToString("<br>") { "`$it`" }} |")
        }
    }
}

private fun parityJson(matrix: ReportMinecraftVersionMatrix): String {
    fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    fun array(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }
    val areas = featureParityEntries().joinToString(",") { entry ->
        """{"area":"${escape(entry.area)}","status":"${escape(entry.status)}","domain":"${escape(entry.domain)}","paperRuntime":"${escape(entry.paperRuntime)}","adapter":"${escape(entry.adapter)}","integration":"${escape(entry.integration)}","recovery":"${escape(entry.recovery)}","evidence":${array(entry.evidence)},"limitation":"${escape(entry.limitation)}"}"""
    }
    val versions = matrix.entries.sortedBy { it.range }.joinToString(",") { entry ->
        """{"id":"${escape(entry.id)}","range":"${escape(entry.normalizedRange)}","compileVerified":${entry.compileEnabled},"bootVerified":${entry.bootSmokeEnabled},"releaseSupported":${entry.releaseSupported},"experimental":${entry.experimental},"adapter":"${escape(entry.adapterSimpleName)}"}"""
    }
    val permissions = superiorSkyblock2PermissionParityEntries().joinToString(",") { entry ->
        """{"scope":"${escape(entry.scope)}","legacyNode":"${escape(entry.legacyNode)}","cloudislandsNode":"${escape(entry.cloudislandsNode)}","status":"${escape(entry.status)}","priority":"${escape(entry.priority)}","note":"${escape(entry.note)}"}"""
    }
    val backlog = superiorSkyblock2PermissionBacklog().joinToString(",") { item ->
        """{"priority":"${escape(item.priority)}","scope":"${escape(item.scope)}","legacyNode":"${escape(item.legacyNode)}","summary":"${escape(item.summary)}","cloudislandsCoverage":"${escape(item.cloudislandsCoverage)}"}"""
    }
    return """{"areas":[$areas],"versions":[$versions],"superiorSkyblock2PermissionParity":{"sourceUrls":${array(superiorSkyblock2PermissionSourceUrls)},"permissions":[$permissions],"backlog":[$backlog]}}""" + System.lineSeparator()
}

private val reportMinecraftVersionMatrixFile = layout.projectDirectory.file("gradle/minecraft-versions.toml").asFile
private val reportMinecraftVersionMatrix = ReportMinecraftVersionMatrix.parse(reportMinecraftVersionMatrixFile)

val superiorSkyblockParityReportFile = rootProject.layout.projectDirectory.dir("../codex-output").file("superiorskyblock2-parity.md")
val minecraftVersionFeatureMatrixFile = rootProject.layout.projectDirectory.dir("../codex-output").file("minecraft-version-feature-matrix.md")
val featureParityJsonFile = rootProject.layout.projectDirectory.dir("../codex-output").file("parity.json")

tasks.register("verifyFeatureParityEvidence") {
    group = "verification"
    description = "Verifies README feature parity claims and writes detailed parity evidence outside the repository."
    inputs.file(layout.projectDirectory.file("README.md"))
    inputs.file(reportMinecraftVersionMatrixFile)
    inputs.file(layout.projectDirectory.file("cloudislands-paper/src/main/resources/plugin.yml"))
    inputs.file(layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandCommandPermission.java"))
    inputs.file(layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"))
    inputs.files(featureParityEntries().flatMap { entry -> entry.evidence }.map { path -> layout.projectDirectory.file(path) })
    outputs.files(superiorSkyblockParityReportFile, minecraftVersionFeatureMatrixFile, featureParityJsonFile)
    doLast {
        verifySuperiorSkyblock2PermissionParityMatrix()
        val missingEvidence = featureParityEntries()
            .flatMap { entry -> entry.evidence }
            .distinct()
            .filterNot { path -> layout.projectDirectory.file(path).asFile.exists() }
        if (missingEvidence.isNotEmpty()) {
            throw GradleException("Feature parity evidence files are missing: ${missingEvidence.joinToString(", ")}")
        }
        val readme = layout.projectDirectory.file("README.md").asFile.readText()
        val expected = featureParityReadmeBlock()
        val start = "<!-- feature-parity:start -->"
        val end = "<!-- feature-parity:end -->"
        val block = Regex("(?s)${Regex.escape(start)}.*?${Regex.escape(end)}")
            .find(readme)
            ?.value
            ?: throw GradleException("README feature parity markers are missing")
        if (block.trim() != expected.trim()) {
            throw GradleException("README feature parity table has drifted from verified evidence")
        }
        val parityFile = superiorSkyblockParityReportFile.asFile
        val versionFile = minecraftVersionFeatureMatrixFile.asFile
        val jsonFile = featureParityJsonFile.asFile
        parityFile.parentFile.mkdirs()
        parityFile.writeText(featureParityMarkdown())
        versionFile.writeText(minecraftVersionFeatureMatrixMarkdown(reportMinecraftVersionMatrix))
        jsonFile.writeText(parityJson(reportMinecraftVersionMatrix))
    }
}

tasks.register("verifyFeatureParityMatrix") {
    group = "verification"
    description = "Compatibility alias for the feature parity evidence gate."
    dependsOn(tasks.named("verifyFeatureParityEvidence"))
}

private fun editReportEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")

private fun editReportArray(values: Iterable<String>): String =
    values.joinToString(prefix = "[", postfix = "]") { "\"${editReportEscape(it)}\"" }

private fun editReportObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(prefix = "{", postfix = "}") { (key, value) -> "\"$key\":$value" }

private fun editReportString(value: String): String = "\"${editReportEscape(value)}\""

private fun editReportWrite(file: File, text: String) {
    file.parentFile.mkdirs()
    file.writeText(text)
}

private fun editReportFeatureEntries(scope: String): List<FeatureParityEntry> =
    featureParityEntries().filter {
        val area = it.area.lowercase()
        when (scope) {
            "player" -> !area.contains("admin") && !area.contains("migration")
            "admin" -> area.contains("admin") || it.evidence.any { evidence -> evidence.contains("admin", ignoreCase = true) }
            "migration" -> area.contains("migration") || it.evidence.any { evidence -> evidence.contains("migration", ignoreCase = true) }
            else -> true
        }
    }

private fun editReportParityJson(scope: String, entries: List<FeatureParityEntry>): String {
    val areas = entries.joinToString(",") { entry ->
        editReportObject(
            "area" to editReportString(entry.area),
            "status" to editReportString(
                when (entry.status) {
                    "IMPLEMENTED_VERIFIED" -> "SUPPORTED"
                    "IMPLEMENTED_UNVERIFIED" -> "PARTIAL"
                    "NOT_APPLICABLE", "INTENTIONALLY_UNSUPPORTED" -> "NOT_A_GOAL"
                    else -> entry.status
                }
            ),
            "priority" to editReportString(if (entry.status == "PLANNED") "P2" else "P0"),
            "domain" to editReportString(entry.domain),
            "paperRuntime" to editReportString(entry.paperRuntime),
            "adapter" to editReportString(entry.adapter),
            "integration" to editReportString(entry.integration),
            "recovery" to editReportString(entry.recovery),
            "evidence" to editReportArray(entry.evidence),
            "limitation" to editReportString(entry.limitation)
        )
    }
    return editReportObject(
        "report" to editReportString("cloudislands-$scope-feature-parity"),
        "unknownForbidden" to "true",
        "unknownEntries" to "[]",
        "entries" to "[$areas]"
    ) + System.lineSeparator()
}

tasks.register("generateEditMdRequiredReports") {
    group = "verification"
    description = "Generates the build/reports surfaces explicitly required by edit.md."
    dependsOn(tasks.named("verifyFeatureParityEvidence"))
    outputs.files(
        layout.buildDirectory.file("reports/parity/superiorskyblock2-parity.json"),
        layout.buildDirectory.file("reports/parity/superiorskyblock2-parity.txt"),
        layout.buildDirectory.file("reports/parity/cloudislands-player-feature-parity.json"),
        layout.buildDirectory.file("reports/parity/cloudislands-admin-feature-parity.json"),
        layout.buildDirectory.file("reports/parity/cloudislands-migration-parity.json"),
        layout.buildDirectory.file("reports/commands/cloudislands-command-surface.json"),
        layout.buildDirectory.file("reports/commands/cloudislands-permission-surface.json"),
        layout.buildDirectory.file("reports/commands/cloudislands-gui-action-surface.json"),
        layout.buildDirectory.file("reports/cloudislands/core-route-surface.json"),
        layout.buildDirectory.file("reports/cloudislands/typed-client-surface.json"),
        layout.buildDirectory.file("reports/cloudislands/config-v2-surface.json"),
        layout.buildDirectory.file("reports/cloudislands/integration-surface.json"),
        layout.buildDirectory.file("reports/security/redaction-check.json"),
        layout.buildDirectory.file("reports/migration/superiorskyblock2-migration-capability-map.json")
    )
    doLast {
        val reportRoot = layout.buildDirectory.dir("reports").get().asFile
        val parityJson = parityJson(reportMinecraftVersionMatrix)
        val parityText = featureParityMarkdown()
        val commandSource = layout.projectDirectory.file("cloudislands-protocol/src/main/java/kr/lunaf/cloudislands/protocol/command/IslandPlayerCommandRegistry.java").asFile.readText()
        val adminCommandSource = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java").asFile.readText()
        val permissionSource = layout.projectDirectory.file("cloudislands-paper/src/main/resources/plugin.yml").asFile.readText()
        val guiSource = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/GuiActionParser.java").asFile.readText()
        val routesSource = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/CoreRouteModules.java").asFile.readText()
        val clientSource = layout.projectDirectory.file("cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java").asFile.readText()
        val configSource = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/config/CoreServiceConfig.java").asFile.readText()
        val integrationReport = layout.buildDirectory.file("reports/cloudislands/integrations.json").get().asFile
        val redactionPolicyPresent = listOf(
            "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigV2Validator.java",
            "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/audit/AuditPayloadRedactor.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminDiagnosticRedactor.java"
        ).all { layout.projectDirectory.file(it).asFile.isFile }
        val migrationPolicy = layout.projectDirectory.file("cloudislands-migration/src/main/java/kr/lunaf/cloudislands/migration/superior/MigrationSafetyPolicy.java").asFile.readText()
        val migrationRoutes = layout.projectDirectory.file("cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/SuperiorSkyblock2MigrationRoutes.java").asFile.readText()

        editReportWrite(File(reportRoot, "parity/superiorskyblock2-parity.json"), parityJson)
        editReportWrite(File(reportRoot, "parity/superiorskyblock2-parity.txt"), parityText)
        editReportWrite(File(reportRoot, "parity/cloudislands-player-feature-parity.json"), editReportParityJson("player", editReportFeatureEntries("player")))
        editReportWrite(File(reportRoot, "parity/cloudislands-admin-feature-parity.json"), editReportParityJson("admin", editReportFeatureEntries("admin")))
        editReportWrite(File(reportRoot, "parity/cloudislands-migration-parity.json"), editReportParityJson("migration", editReportFeatureEntries("migration")))

        editReportWrite(File(reportRoot, "commands/cloudislands-command-surface.json"), editReportObject(
            "report" to editReportString("cloudislands-command-surface"),
            "canonicalRegistry" to editReportString("cloudislands-protocol:IslandPlayerCommandRegistry"),
            "paperAdminCatalog" to "${adminCommandSource.contains("ciadmin migrate superiorskyblock2 unlock --confirm <token>")}",
            "playerRegistryPresent" to "${commandSource.contains("IslandPlayerCommandRegistry")}",
            "paperVelocityDriftGate" to editReportString("verifyPaperCommandCoverage,verifyVelocityCommandCoverage")
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "commands/cloudislands-permission-surface.json"), editReportObject(
            "report" to editReportString("cloudislands-permission-surface"),
            "pluginYmlPermissions" to "${permissionSource.contains("cloudislands.admin")}",
            "coverageGate" to editReportString("verifyPermissionCoverage"),
            "adminMigrationPermission" to "${permissionSource.contains("cloudislands.admin.migrate-superiorskyblock2")}"
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "commands/cloudislands-gui-action-surface.json"), editReportObject(
            "report" to editReportString("cloudislands-gui-action-surface"),
            "parserPresent" to "${guiSource.contains("GuiActionParser")}",
            "coverageGate" to editReportString("verifyGuiActionCoverage")
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "cloudislands/core-route-surface.json"), editReportObject(
            "report" to editReportString("cloudislands-core-route-surface"),
            "routeModuleRegistry" to "${routesSource.contains("CoreRouteModules")}",
            "coverageGate" to editReportString("verifyRouteDomainCoverage")
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "cloudislands/typed-client-surface.json"), editReportObject(
            "report" to editReportString("cloudislands-typed-client-surface"),
            "coreApiClientPresent" to "${clientSource.contains("interface CoreApiClient") || clientSource.contains("class CoreApiClient")}",
            "coverageGate" to editReportString("verifyApiRouteCoverage")
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "cloudislands/config-v2-surface.json"), editReportObject(
            "report" to editReportString("cloudislands-config-v2-surface"),
            "coreConfigPresent" to "${configSource.contains("CoreServiceConfig")}",
            "coverageGates" to editReportArray(listOf("verifyCoreConfigCoverage", "verifyPaperConfigCoverage"))
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "cloudislands/integration-surface.json"), if (integrationReport.isFile) integrationReport.readText() else editReportObject(
            "report" to editReportString("cloudislands-integration-surface"),
            "coverageGate" to editReportString("verifyIntegrationMatrix"),
            "integrationsReportMissing" to "true"
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "security/redaction-check.json"), editReportObject(
            "report" to editReportString("cloudislands-redaction-check"),
            "secretRedactionPolicyPresent" to "$redactionPolicyPresent",
            "coverageGates" to editReportArray(listOf("verifyReleaseSecurityGate", "verifyPermissionCoverage"))
        ) + System.lineSeparator())
        editReportWrite(File(reportRoot, "migration/superiorskyblock2-migration-capability-map.json"), editReportObject(
            "report" to editReportString("superiorskyblock2-migration-capability-map"),
            "commands" to editReportArray(listOf("scan", "dry-run", "status", "approve", "import", "verify", "compare", "rollback-plan", "report", "unlock")),
            "spacedCommandAlias" to "${migrationPolicy.contains("/ciadmin migrate superiorskyblock2 scan")}",
            "unlockRoute" to "${migrationRoutes.contains("/v1/admin/migrations/superiorskyblock2/unlock")}",
            "coverageGate" to editReportString("verifySatisMigrationReportCoverage")
        ) + System.lineSeparator())

        val required = outputs.files.files.toList()
        val missing = required.filterNot { it.isFile && it.length() > 0L }
        if (missing.isNotEmpty()) {
            throw GradleException("edit.md required reports were not generated: ${missing.joinToString { it.path }}")
        }
        val unknownReports = required.filter { it.readText().contains("UNKNOWN") }
        if (unknownReports.isNotEmpty()) {
            throw GradleException("edit.md required reports must not contain UNKNOWN parity status: ${unknownReports.joinToString { it.path }}")
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("generateEditMdRequiredReports"))
}
