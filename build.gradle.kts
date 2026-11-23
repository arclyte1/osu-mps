import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("io.ktor.plugin") version "3.6.1"
    application
}

group = "osu.mps"
version = "0.1.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.6.1")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.1")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.6.1")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.6.1")
    implementation("io.ktor:ktor-server-config-yaml:3.6.1")
    implementation("io.ktor:ktor-client-core-jvm:3.6.1")
    implementation("io.ktor:ktor-client-cio-jvm:3.6.1")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:3.6.1")
    implementation("io.ktor:ktor-client-logging-jvm:3.6.1")

    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.61.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("io.insert-koin:koin-ktor:4.2.2")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.2")
    implementation("org.postgresql:postgresql:42.7.14")

    implementation("ch.qos.logback:logback-classic:1.6.8")

    implementation("com.networknt:json-schema-validator:1.5.9")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.23.0")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
