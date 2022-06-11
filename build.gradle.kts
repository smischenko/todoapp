import org.flywaydb.core.Flyway
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Strategy
import org.jooq.meta.jaxb.Target

val kotlin_version: String by project
val ktor_version: String by project
val logback_version: String by project
val arrow_version: String by project
val postgres_version: String by project
val hikari_version: String by project
val flyway_version: String by project
val jooq_version: String by project

plugins {
    application
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

group = "todoapp"
version = "0.0.1"
application {
    mainClass.set("todoapp.MainKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation(platform("io.arrow-kt:arrow-stack:$arrow_version"))
    implementation("io.arrow-kt:arrow-fx-coroutines")
    implementation("io.arrow-kt:arrow-fx-stm")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.flywaydb:flyway-core:$flyway_version")
    implementation("org.jooq:jooq:$jooq_version")
    implementation("org.jooq:jooq-kotlin:$jooq_version")
    implementation("org.testcontainers:postgresql:1.17.2")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.testcontainers:postgresql:1.17.2")
        classpath("org.flywaydb:flyway-core:8.5.12")
        classpath("org.postgresql:postgresql:42.3.6")
        classpath("org.jooq:jooq-codegen:3.16.6")
    }
}

sourceSets.main {
    java.srcDirs("build/generated-src/kotlin")
}

tasks.register("jooq-codegen") {
    doLast {
        val db = KPostgreSQLContainer("postgres:14.3")
            .withUsername("postgres")
            .withDatabaseName("postgres")
            .withPassword("postgres")
        db.start()

        Flyway.configure()
            .dataSource(db.jdbcUrl, "postgres", "postgres")
            .locations("filesystem:src/main/resources/db/migration")
            .load()
            .migrate()

        GenerationTool.generate(
            Configuration()
                .withJdbc(
                    Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(db.jdbcUrl)
                        .withUsername("postgres")
                        .withPassword("postgres")
                )
                .withGenerator(
                    Generator()
                        .withName("org.jooq.codegen.KotlinGenerator")
                        .withDatabase(
                            Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withInputSchema("public")
                        )
                        .withGenerate(Generate())
                        .withTarget(
                            Target()
                                .withPackageName("todoapp.jooq")
                                .withDirectory("$buildDir/generated-src/kotlin")
                        )
                        .withStrategy(Strategy())
                )
        )

        db.stop()
    }
}

tasks.compileKotlin {
    dependsOn("jooq-codegen")
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
}

class KPostgreSQLContainer(image: String) :
    org.testcontainers.containers.PostgreSQLContainer<KPostgreSQLContainer>(image)