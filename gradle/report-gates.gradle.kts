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
        "Paper 1.21.x boot smoke loads the plugin",
        "1.21.x release adapter; 26.1/26.2 compile adapters",
        "ciIntegrationSmoke verifies cross-Core create, job, route, session, consume",
        "node-down recovery restore is covered by ciIntegrationSmoke",
        listOf(
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflowRestoreTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/platform/compatibility/Paper121FamilyAdapter.java",
            "scripts/ci/core_integration_smoke.py"
        ),
        "26.1 and 26.2 stay compile-only until official bootable Paper builds are available"
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
        "Paper protection listeners and cache paths have unit coverage",
        "Bukkit-facing behavior is kept inside Paper runtime code",
        "unit verified; real-player destructive-action smoke is not part of CI",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/ProtectionControllerTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/IslandGameplayFlagListener.java",
            "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/permission/defaults/DefaultIslandPermissionsTest.java"
        ),
        "runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests"
    ),
    FeatureParityEntry(
        "ranking/level/worth/block values",
        "IMPLEMENTED_VERIFIED",
        "ranking and dirty recalculation logic have service tests",
        "Paper-facing values compile; no per-version runtime divergence is claimed",
        "version-neutral",
        "service-level verified",
        "not recovery-specific",
        listOf(
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/DirtyRankingRecalculationTaskTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/LevelWorthSystemPolicyTest.java",
            "cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/hook/PlaceholderFeaturePolicyTest.java"
        ),
        "worth economics beyond configured value calculations are not release-certified"
    ),
    FeatureParityEntry(
        "upgrades/size/border/biome",
        "IMPLEMENTED_VERIFIED",
        "upgrade effects apply size, limits, fly, generator tier, biome validation, and player border policy",
        "Paper commands compile and tests cover command policy plus border runtime calculation",
        "Paper adapter isolates version-sensitive runtime access",
        "verifyUpgradeEffectCoverage covers Core upgrade effects, biome normalization, and Paper world-border policy",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/platform/compatibility/PaperPlatformBoundaryTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/upgrade/UpgradeEffectApplierTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutesTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/IslandBorderRuntimePolicyTest.java"
        ),
        "operator live-server biome painting acceptance is still recommended; CI verifies the Core mutation and Paper border application policy"
    ),
    FeatureParityEntry(
        "bank/economy/missions/challenges/generators/limits",
        "IMPLEMENTED_VERIFIED",
        "bank safety, economy hooks, mission triggers/rewards, challenges, generator rules, and limits have verification gates",
        "Paper mission listeners, bank rollback UX, and generator listeners have targeted tests",
        "version-neutral plus Paper/Satis runtime boundaries",
        "verifyMissionEventProgress, verifyMissionRewardCoverage, verifyGeneratorRules, verifyEconomyTransactionSafety, and verifyIntegrationRuntimeSmoke cover the current scope",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/IslandMissionProgressListener.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/mission/MissionRewardServiceTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/BankUseCaseTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/generator/IslandGeneratorListener.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/GeneratorRoutesTest.java"
        ),
        "operator live-server economy/provider acceptance is still recommended; fixture-backed priority Vault certification is enforced"
    ),
    FeatureParityEntry(
        "chat/logs/reviews",
        "IMPLEMENTED_VERIFIED",
        "chat listener, audit/log routes, visitor stats, and review moderation have verification gates",
        "Paper chat listener compiles under matrix and Core review moderation has route/repository/schema tests",
        "version-neutral where possible",
        "verifyReviewModerationCoverage plus Core audit/visitor route tests cover current workflow",
        "audit log replay is covered in Core tests",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java",
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
        "IMPLEMENTED_VERIFIED",
        "integration policy, localization files, GUI components, and priority plugin runtime certification fixtures exist",
        "Paper integration registry reports explicit operation states and priority runtime certification",
        "plugin-specific adapters are active where implemented",
        "verifyIntegrationRuntimeSmoke proves priority plugin operation smoke fixtures for Vault, LuckPerms, PlaceholderAPI, WorldEdit, and CoreProtect",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java",
            "cloudislands-paper/src/main/resources/config-v2/integrations.yml",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandConfirmationMenu.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertification.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertificationTest.java"
        ),
        "full third-party server farms remain operator acceptance; CI verifies fixture-backed priority operation certification"
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
    permissionParity("player", "superior.island.coop", "cloudislands.island.trust", "COVERED_BY", "", "temporary cooperation is represented by trust/co-op commands"),
    permissionParity("player", "superior.island.coops", "cloudislands.island.members", "COVERED_BY", "", "co-op listing is covered by member management"),
    permissionParity("player", "superior.island.counts", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "block counts are in the progression/value command group"),
    permissionParity("player", "superior.island.create", "cloudislands.island.create", "SUPPORTED_VERIFIED", "", "island creation maps directly"),
    permissionParity("player", "superior.island.delwarp", "cloudislands.island.setwarp", "SUPPORTED_VERIFIED", "", "warp creation, publication, and deletion share the setwarp grant"),
    permissionParity("player", "superior.island.demote", "cloudislands.island.demote", "SUPPORTED_VERIFIED", "", "member demotion maps directly"),
    permissionParity("player", "superior.island.deposit", "cloudislands.island.bank.deposit", "SUPPORTED_VERIFIED", "", "bank deposit maps directly"),
    permissionParity("player", "superior.island.disband", "cloudislands.island.delete", "COVERED_BY", "", "permanent island disband maps to the guarded delete/reset flow"),
    permissionParity("player", "superior.island.expel", "cloudislands.island.kick", "COVERED_BY", "", "visitor expel is grouped with membership enforcement"),
    permissionParity("player", "superior.island.fly", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "island fly toggle is a settings command"),
    permissionParity("player", "superior.island.help", "cloudislands.island.menu", "SUPPORTED_VERIFIED", "", "help and command listing are menu-safe commands"),
    permissionParity("player", "superior.island.invite", "cloudislands.island.invite", "SUPPORTED_VERIFIED", "", "member invite maps directly"),
    permissionParity("player", "superior.island.kick", "cloudislands.island.kick", "SUPPORTED_VERIFIED", "", "member kick maps directly"),
    permissionParity("player", "superior.island.lang", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "language selection is in settings"),
    permissionParity("player", "superior.island.leave", "cloudislands.island.leave", "SUPPORTED_VERIFIED", "", "leave maps directly"),
    permissionParity("player", "superior.island.members", "cloudislands.island.members", "SUPPORTED_VERIFIED", "", "member panel maps directly"),
    permissionParity("player", "superior.island.mission", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "mission completion is part of progression"),
    permissionParity("player", "superior.island.missions", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "mission/challenge menus are part of progression"),
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
    permissionParity("player", "superior.island.setdiscord", "PLANNED:island-social-profile", "PLANNED", "P2", "legacy social metadata import is tracked but not required for core gameplay parity"),
    permissionParity("player", "superior.island.setpaypal", "PLANNED:island-social-profile", "PLANNED", "P2", "legacy donation metadata import is tracked but not required for core gameplay parity"),
    permissionParity("player", "superior.island.setrole", "cloudislands.island.promote", "COVERED_BY", "", "role assignment/edit is grouped with promotion and role editing"),
    permissionParity("player", "superior.island.setteleport", "cloudislands.island.sethome", "SUPPORTED_VERIFIED", "", "teleport point setup maps to sethome"),
    permissionParity("player", "superior.island.settings", "cloudislands.island.settings", "SUPPORTED_VERIFIED", "", "settings menu maps directly"),
    permissionParity("player", "superior.island.setwarp", "cloudislands.island.setwarp", "SUPPORTED_VERIFIED", "", "warp creation maps directly"),
    permissionParity("player", "superior.island.show", "cloudislands.island.menu", "COVERED_BY", "", "island info/show is available through the overview/menu permission"),
    permissionParity("player", "superior.island.stacker.*", "PLANNED:block-stacker-compatibility", "PLANNED", "P2", "block stacker permission compatibility is addon-dependent"),
    permissionParity("player", "superior.island.stacker.<block-type>", "PLANNED:block-stacker-compatibility", "PLANNED", "P2", "per-block stacker grants remain addon-dependent"),
    permissionParity("player", "superior.island.team", "cloudislands.island.members", "COVERED_BY", "", "team listing maps to member management"),
    permissionParity("player", "superior.island.teamchat", "cloudislands.island.chat", "SUPPORTED_VERIFIED", "", "team chat maps directly"),
    permissionParity("player", "superior.island.teleport", "cloudislands.island.home", "SUPPORTED_VERIFIED", "", "island teleport maps to home access"),
    permissionParity("player", "superior.island.toggle", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "toggle commands are grouped with environment controls"),
    permissionParity("player", "superior.island.toggle.blocks", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "block count toggles are grouped with environment controls"),
    permissionParity("player", "superior.island.toggle.border", "cloudislands.island.environment", "SUPPORTED_VERIFIED", "", "border display toggle is grouped with environment controls"),
    permissionParity("player", "superior.island.top", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "top island views are part of progression/ranking"),
    permissionParity("player", "superior.island.transfer", "cloudislands.island.transfer", "SUPPORTED_VERIFIED", "", "ownership transfer maps directly"),
    permissionParity("player", "superior.island.uncoop", "cloudislands.island.kick", "COVERED_BY", "", "temporary cooperation removal is grouped with membership enforcement"),
    permissionParity("player", "superior.island.upgrade", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "upgrade menu and purchase are part of progression"),
    permissionParity("player", "superior.island.value", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "worth/value lookup is part of progression"),
    permissionParity("player", "superior.island.values", "cloudislands.island.progression", "SUPPORTED_VERIFIED", "", "block values lookup is part of progression"),
    permissionParity("player", "superior.island.visit", "cloudislands.island.warp", "SUPPORTED_VERIFIED", "", "public island visit maps to visit/warp access"),
    permissionParity("player", "superior.island.visitors", "cloudislands.island.visitor-stats", "SUPPORTED_VERIFIED", "", "visitor statistics maps directly"),
    permissionParity("player", "superior.island.warp", "cloudislands.island.warp", "SUPPORTED_VERIFIED", "", "warp travel maps directly"),
    permissionParity("player", "superior.island.warps", "cloudislands.island.warp", "SUPPORTED_VERIFIED", "", "warp list maps directly"),
    permissionParity("player", "superior.island.withdraw", "cloudislands.island.bank.withdraw", "SUPPORTED_VERIFIED", "", "bank withdrawal maps directly")
)

private fun adminPermissionParityEntries(): List<PermissionParityEntry> = listOf(
    permissionParity("admin", "superior.admin.*", "cloudislands.admin", "COVERED_BY", "", "root admin grant maps to the CloudIslands admin root"),
    permissionParity("admin", "superior.admin.add", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member add is exposed by /ciadmin island member add with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addbanklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive bank-limit mutation is exposed by /ciadmin island addbanklimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.addblocklimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct additive block-limit mutation is exposed by /ciadmin island addblocklimit using shared BLOCK_AMOUNT limit keys"),
    permissionParity("admin", "superior.admin.addbonus", "PLANNED:bonus-compatibility", "PLANNED", "P2", "legacy bonus subsystem is tracked separately from core upgrade rules"),
    permissionParity("admin", "superior.admin.addcooplimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "co-op/member capacity additive mutation is exposed by /ciadmin island addcooplimit through the enforced MEMBERS limit key"),
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
    permissionParity("admin", "superior.admin.bonus", "PLANNED:bonus-compatibility", "PLANNED", "P2", "legacy bonus inspection remains outside core command parity"),
    permissionParity("admin", "superior.admin.bypass", "cloudislands.admin.bypass", "SUPPORTED_VERIFIED", "", "admin bypass maps directly"),
    permissionParity("admin", "superior.admin.bypass.*", "cloudislands.admin.bypass", "COVERED_BY", "", "specific bypass grants collapse to the CloudIslands bypass root"),
    permissionParity("admin", "superior.admin.bypass.cooldowns", "cloudislands.bypass.cooldown", "SUPPORTED_VERIFIED", "", "cooldown bypass maps directly"),
    permissionParity("admin", "superior.admin.bypass.warmup", "cloudislands.bypass.warmup", "SUPPORTED_VERIFIED", "", "warmup bypass maps directly"),
    permissionParity("admin", "superior.admin.chest", "cloudislands.admin.storage", "COVERED_BY", "", "storage inspection is represented by admin storage commands"),
    permissionParity("admin", "superior.admin.cleargenerator", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct generator clearing is exposed by /ciadmin island cleargenerator with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.close", "cloudislands.admin.island", "COVERED_BY", "", "island activation and lifecycle controls are admin island commands"),
    permissionParity("admin", "superior.admin.cmdall", "PLANNED:admin-broadcast-commands", "PLANNED", "P2", "bulk command dispatch is intentionally separated for operator safety"),
    permissionParity("admin", "superior.admin.count", "cloudislands.admin.block-values", "COVERED_BY", "", "block value/count inspection is covered by block-value and island info surfaces"),
    permissionParity("admin", "superior.admin.data", "cloudislands.admin.support-bundle", "COVERED_BY", "", "diagnostic data export maps to support bundles"),
    permissionParity("admin", "superior.admin.debug", "cloudislands.admin.diagnostics", "COVERED_BY", "", "debug output maps to diagnostics/export commands"),
    permissionParity("admin", "superior.admin.delwarp", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin warp deletion is exposed by /ciadmin island delwarp with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.demote", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member demotion is exposed by /ciadmin island member demote with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.deposit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin bank deposit is exposed by /ciadmin island bank deposit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.disband", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "", "guarded island delete/repair/recover commands cover destructive lifecycle control"),
    permissionParity("admin", "superior.admin.fly", "PLANNED:admin-fly-mutation", "PLANNED", "P2", "direct admin fly toggle remains a convenience backlog item"),
    permissionParity("admin", "superior.admin.givedisbands", "PLANNED:disband-quota-compatibility", "PLANNED", "P2", "legacy disband quota commands are tracked as compatibility-only backlog"),
    permissionParity("admin", "superior.admin.ignore", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "ranking ignore state is exposed by /ciadmin island ignore with typed Core audit and cache-invalidation coverage"),
    permissionParity("admin", "superior.admin.join", "PLANNED:admin-member-join", "PLANNED", "P1", "forced admin join is not exposed; teleport/inspection exists separately"),
    permissionParity("admin", "superior.admin.kick", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member kick is exposed by /ciadmin island member kick with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.mission", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct mission completion/progress mutation is exposed by /ciadmin island mission with typed Core audit, event, and reward coverage"),
    permissionParity("admin", "superior.admin.modules", "cloudislands.admin.addons", "COVERED_BY", "", "module/addon status maps to addon administration"),
    permissionParity("admin", "superior.admin.msg", "PLANNED:admin-island-messaging", "PLANNED", "P2", "direct message-to-island command is not part of the core admin surface"),
    permissionParity("admin", "superior.admin.msgall", "PLANNED:admin-island-messaging", "PLANNED", "P2", "bulk island messaging is not part of the core admin surface"),
    permissionParity("admin", "superior.admin.name", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin island rename is exposed by /ciadmin island rename with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.open", "cloudislands.admin.island", "COVERED_BY", "", "island activation/lifecycle commands cover availability control"),
    permissionParity("admin", "superior.admin.openmenu", "PLANNED:admin-menu-proxy", "PLANNED", "P2", "remote menu opening is a compatibility convenience backlog item"),
    permissionParity("admin", "superior.admin.promote", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin member promotion is exposed by /ciadmin island member promote with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.purge", "cloudislands.admin.storage", "COVERED_BY", "", "storage and recovery tooling covers destructive data maintenance paths"),
    permissionParity("admin", "superior.admin.rankup", "PLANNED:admin-upgrade-mutations", "PLANNED", "P1", "direct admin rankup mutation is not yet exposed"),
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
    permissionParity("admin", "superior.admin.setchestrow", "PLANNED:admin-warehouse-size", "PLANNED", "P1", "direct warehouse row mutation is not yet exposed"),
    permissionParity("admin", "superior.admin.setcooplimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "co-op/member capacity mutation is exposed by /ciadmin island setcooplimit through the enforced MEMBERS limit key"),
    permissionParity("admin", "superior.admin.setcropgrowth", "cloudislands.admin.setcropgrowth", "SUPPORTED_VERIFIED", "", "crop growth mutation maps directly"),
    permissionParity("admin", "superior.admin.setdisbands", "PLANNED:disband-quota-compatibility", "PLANNED", "P2", "legacy disband quota mutation is tracked as compatibility-only backlog"),
    permissionParity("admin", "superior.admin.seteffect", "cloudislands.admin.seteffect", "SUPPORTED_VERIFIED", "", "effect mutation maps directly"),
    permissionParity("admin", "superior.admin.setentitylimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct entity-limit mutation is exposed by /ciadmin island setentitylimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setgenerator", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct generator mutation is exposed by /ciadmin island setgenerator with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setislandpreview", "cloudislands.admin.templates", "COVERED_BY", "", "template preview management maps to template administration"),
    permissionParity("admin", "superior.admin.setleader", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin leader reassignment is exposed by /ciadmin island member setleader with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setmobdrops", "cloudislands.admin.setmobdrops", "SUPPORTED_VERIFIED", "", "mob drop mutation maps directly"),
    permissionParity("admin", "superior.admin.setpermission", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct admin permission mutation is exposed by /ciadmin island setpermission with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setrate", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct reviewer rating mutation is exposed by /ciadmin island setrate through typed review mutation"),
    permissionParity("admin", "superior.admin.setrolelimit", "PLANNED:admin-limit-mutations", "PLANNED", "P1", "direct role-limit mutation is not yet exposed"),
    permissionParity("admin", "superior.admin.setsettings", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct island flag mutation is exposed by /ciadmin island setsettings with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setsize", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct island size mutation is exposed by /ciadmin island setsize with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.setspawn", "PLANNED:admin-spawn-mutation", "PLANNED", "P2", "server spawn mutation is outside current island admin scope"),
    permissionParity("admin", "superior.admin.setspawnerrates", "cloudislands.admin.setspawnerrates", "SUPPORTED_VERIFIED", "", "spawner rate mutation maps directly"),
    permissionParity("admin", "superior.admin.setteamlimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct team-limit mutation is exposed by /ciadmin island setteamlimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.settings", "cloudislands.admin.config", "COVERED_BY", "", "in-game config editor maps to config administration"),
    permissionParity("admin", "superior.admin.setupgrade", "cloudislands.admin.upgrade-rules", "COVERED_BY", "", "upgrade rule mutation maps to upgrade-rules administration"),
    permissionParity("admin", "superior.admin.setwarpslimit", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "direct warp-limit mutation is exposed by /ciadmin island setwarpslimit with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.show", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "", "island info maps to admin island commands"),
    permissionParity("admin", "superior.admin.spawn", "cloudislands.admin.node", "COVERED_BY", "", "node/world routing covers operational spawn context"),
    permissionParity("admin", "superior.admin.spy", "PLANNED:admin-chat-spy", "PLANNED", "P2", "chat spy is tracked as moderation convenience backlog"),
    permissionParity("admin", "superior.admin.stats", "cloudislands.admin.metrics", "SUPPORTED_VERIFIED", "", "stats maps to metrics"),
    permissionParity("admin", "superior.admin.syncbonus", "PLANNED:bonus-compatibility", "PLANNED", "P2", "legacy bonus sync remains compatibility-only backlog"),
    permissionParity("admin", "superior.admin.syncupgrades", "cloudislands.admin.upgrade-rules", "COVERED_BY", "", "upgrade synchronization maps to upgrade rule administration"),
    permissionParity("admin", "superior.admin.teleport", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "", "admin island teleport maps to admin island commands"),
    permissionParity("admin", "superior.admin.title", "PLANNED:admin-island-messaging", "PLANNED", "P2", "title messaging is not part of the core admin surface"),
    permissionParity("admin", "superior.admin.titleall", "PLANNED:admin-island-messaging", "PLANNED", "P2", "bulk title messaging is not part of the core admin surface"),
    permissionParity("admin", "superior.admin.unignore", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P1", "ranking unignore state is exposed by /ciadmin island unignore with typed Core audit and cache-invalidation coverage"),
    permissionParity("admin", "superior.admin.withdraw", "cloudislands.admin.island", "SUPPORTED_VERIFIED", "P0", "direct admin bank withdrawal is exposed by /ciadmin island bank withdraw with typed Core audit and event coverage"),
    permissionParity("admin", "superior.admin.world", "cloudislands.admin.storage", "COVERED_BY", "", "world/storage verification maps to admin storage commands")
)

private fun superiorSkyblock2PermissionParityEntries(): List<PermissionParityEntry> =
    playerPermissionParityEntries() + adminPermissionParityEntries()

private fun superiorSkyblock2PermissionBacklog(): List<PermissionBacklogItem> =
    superiorSkyblock2PermissionParityEntries()
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
    val backlogPriorities = superiorSkyblock2PermissionBacklog().map { it.priority }.toSet()
    val missingBacklogPriorities = setOf("P0", "P1", "P2") - backlogPriorities
    if (missingBacklogPriorities.isNotEmpty()) {
        throw GradleException("SuperiorSkyblock2 permission backlog must include P0/P1/P2 groups; missing=${missingBacklogPriorities.joinToString()}")
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
