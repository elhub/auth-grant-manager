plugins {
    alias(libs.plugins.elhub.gradle.plugin)
    alias(libs.plugins.google.cloud.tools.jib)
    alias(libs.plugins.ktor.plugin)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.ksp.plugin)
    alias(libs.plugins.liquibase.plugin)
    alias(libs.plugins.gradle.docker)
}

buildscript {
    dependencies {
        classpath(libs.database.liquibase.core)
    }
}

sourceSets {
    val integrationTest by creating {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
        java.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
    }
}

configurations {
    val integrationTestImplementation by getting {
        extendsFrom(configurations["testImplementation"])
    }
    val integrationTestRuntimeOnly by getting {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}

/*sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output + configurations["testImplementation"]
        runtimeClasspath += output + compileClasspath
    }
    /*
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + configurations["integrationTestImplementation"]
        runtimeClasspath += sourceSets.main.get().output + configurations["integrationTestRuntimeOnly"]
    }
    */
}

configurations {
    create("integrationTestImplementation") {
        extendsFrom(configurations["testImplementation"])
    }
    create("integrationTestRuntimeOnly") {
        extendsFrom(configurations["testRuntimeOnly"])
    }
}*/

dependencies {
    // Ktor
    implementation(libs.bundles.ktor.server)
    // Koin
    implementation(libs.bundles.ktor.koin)
    ksp(libs.di.koin.ksp.compiler)
    // Logging
    implementation(libs.logging.logback.classic)
    // Monitoring
    implementation(libs.bundles.ktor.monitoring)
    // Liquibase
    liquibaseRuntime(libs.database.liquibase.core)
    liquibaseRuntime(libs.cli.picocli)
    liquibaseRuntime(libs.serialization.yaml.snakeyaml)
    liquibaseRuntime(libs.database.postgresql)
    // Unit Testing
    testImplementation(libs.test.ktor.server.test.host)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.kotest.assertions.core)
    // Integration Testing
    "integrationTestImplementation"(libs.test.ktor.server.test.host)
    "integrationTestImplementation"(libs.test.kotest.runner.junit5)
    "integrationTestImplementation"(libs.test.kotest.assertions.core)
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
    arg("KOIN_DEFAULT_MODULE", "true")
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

liquibase {
    jvmArgs = arrayOf(
        "-Dliquibase.command.url=jdbc:postgresql://localhost:5432/postgres",
        "-Dliquibase.command.username=postgres",
        "-Dliquibase.command.password=postgres",
        "-Dliquibase.command.driver=org.postgresql.Driver",
        "-Dliquibase.command.changeLogFile=db/db-changelog.yaml"
    )
    activities.register("main")
}

dockerCompose {
    createNested("database").apply {
        useComposeFiles.set(listOf("db/db-compose.yaml"))
    }
}

tasks.named("run").configure {
    dependsOn(tasks.named("databaseComposeUp"))
    dependsOn(tasks.named("liquibaseUpdate"))
}

val integrationTest = task<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter("test")
}

tasks.named("integrationTest").configure {
    dependsOn(tasks.named("databaseComposeUp"))
    dependsOn(tasks.named("liquibaseUpdate"))
    finalizedBy(tasks.named("databaseComposeDown"))
}
