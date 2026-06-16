plugins { application }

dependencies {
    implementation(project(":cloudislands-api"))
    implementation(project(":cloudislands-common"))
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-storage"))
    implementation(project(":cloudislands-migration"))
    runtimeOnly("org.postgresql:postgresql:42.7.7")
    runtimeOnly("com.mysql:mysql-connector-j:9.1.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.1")
}

application {
    mainClass.set("kr.lunaf.cloudislands.coreservice.CloudIslandsCoreApplication")
}

tasks.jar {
    manifest {
        attributes(
            "CloudIslands-Core-Setup-Database-Path" to "setup.database",
            "CloudIslands-Core-Setup-Database-Supported-Targets" to "POSTGRESQL,MYSQL,MARIADB,CORE_API",
            "CloudIslands-Core-JDBC-Native-Backend" to "POSTGRESQL",
            "CloudIslands-Core-JDBC-Fallback-Order" to "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC",
            "CloudIslands-Core-Unsupported-JDBC-Policy" to "safe-fallback-with-postgresql-or-in-memory",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false"
        )
    }
}
