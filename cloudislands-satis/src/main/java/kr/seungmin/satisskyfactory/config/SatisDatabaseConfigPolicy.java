package kr.seungmin.satisskyfactory.config;

import java.util.List;

public final class SatisDatabaseConfigPolicy {
    public static final String ENV_TYPE = "CLOUDISLANDS_SATIS_DATABASE_TYPE";
    public static final String ENV_JDBC_URL = "CLOUDISLANDS_SATIS_JDBC_URL";
    public static final String ENV_USERNAME = "CLOUDISLANDS_SATIS_DB_USERNAME";
    public static final String ENV_PASSWORD = "CLOUDISLANDS_SATIS_DB_PASSWORD";
    public static final String SETUP_ROOT = "setup.database";
    public static final String ADDON_ROOT = "addons.cloudislands-satis.database";
    public static final String LEGACY_ROOT = "database";
    public static final String FALLBACK_PRECEDENCE = "env,setup.database,addons.cloudislands-satis.database,database";

    private static final List<String> TYPE_PRIORITY = List.of(
            ENV_TYPE,
            "setup.database.type",
            "addons.cloudislands-satis.database.type",
            "setup.database.core-api.enabled",
            "jdbc-url-inference",
            "setup.database.<backend>",
            "database.type"
    );

    private static final List<String> PATH_PRIORITY = List.of(
            "CLOUDISLANDS_SATIS_DB",
            "setup.database.path",
            "addons.cloudislands-satis.database.path",
            "database.path",
            "setup.database.shared-directory",
            "addons.cloudislands-satis.database.shared-directory",
            "database.shared-directory",
            "setup.database.sqlite-file",
            "addons.cloudislands-satis.database.sqlite-file",
            "database.sqlite-file"
    );

    private static final List<String> COMMON_JDBC_ALIASES = List.of(
            ENV_JDBC_URL,
            "setup.database.jdbc.url",
            "addons.cloudislands-satis.database.jdbc.url",
            "database.jdbc.url"
    );

    private static final List<String> CREDENTIAL_ALIASES = List.of(
            ENV_USERNAME,
            ENV_PASSWORD,
            "setup.database.jdbc.username",
            "setup.database.jdbc.password",
            "addons.cloudislands-satis.database.jdbc.username",
            "addons.cloudislands-satis.database.jdbc.password",
            "database.jdbc.username",
            "database.jdbc.password"
    );

    private SatisDatabaseConfigPolicy() {
    }

    public static List<String> typePriority() {
        return TYPE_PRIORITY;
    }

    public static List<String> pathPriority() {
        return PATH_PRIORITY;
    }

    public static List<String> commonJdbcAliases() {
        return COMMON_JDBC_ALIASES;
    }

    public static List<String> credentialAliases() {
        return CREDENTIAL_ALIASES;
    }

    public static String commonJdbcAliasMetadata() {
        return "setup.database.jdbc.url,setup.database.<backend>.jdbc-url,setup.database.<backend>.url,addons.cloudislands-satis.database.jdbc.url,database.jdbc.url,database.<backend>.url";
    }
}
