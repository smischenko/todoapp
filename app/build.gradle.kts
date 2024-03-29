import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

val kotlin_version = "1.7.0"
val ktor_version = "2.0.2"
val logback_version = "1.2.11"
val arrow_version = "1.2.0"
val postgres_version = "42.3.6"
val hikari_version = "5.0.1"
val flyway_version = "8.5.12"
val jooq_version = "3.16.6"
val micrometer_version = "1.9.0"
val kotest_version = "5.3.1"
val testcontainers_version = "1.17.2"

plugins {
    application
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.7.0"
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
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor_version")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometer_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.zipkin.brave:brave:5.13.3")
    implementation("io.zipkin.reporter2:zipkin-sender-urlconnection:2.16.3")
    implementation(platform("io.arrow-kt:arrow-stack:$arrow_version"))
    implementation("io.arrow-kt:arrow-fx-coroutines")
    implementation("io.arrow-kt:arrow-fx-stm")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")
    implementation("org.flywaydb:flyway-core:$flyway_version")
    implementation("org.jooq:jooq:$jooq_version")
    implementation("org.jooq:jooq-kotlin:$jooq_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotest_version")
    testImplementation("io.kotest:kotest-assertions-core:$kotest_version")
    testImplementation("io.kotest:kotest-assertions-json:$kotest_version")
    testImplementation("org.testcontainers:postgresql:$testcontainers_version")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.33.2")
}

val kotlinJvmOptions: KotlinJvmOptions.() -> Unit = {
    freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
}

tasks.compileKotlin {
    kotlinOptions(kotlinJvmOptions)
}

tasks.compileTestKotlin {
    kotlinOptions(kotlinJvmOptions)
}

tasks.test {
    useJUnitPlatform()
}
