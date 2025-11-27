
val exposedVersion: String = "1.0.0-rc-3"
val kotlinVersion: String = "2.2.20"
val logbackVersion: String = "1.5.21"
val postgresVersion: String = "42.7.8"
val jbcryptVersion: String = "0.4"

plugins {
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

group = "org.burgas"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-migration-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.mindrot:jbcrypt:$jbcryptVersion")
    implementation("io.ktor:ktor-server-auth:3.3.2")
    implementation("io.ktor:ktor-server-cors:3.3.2")
    implementation("io.ktor:ktor-server-csrf:3.3.2")
    implementation("io.ktor:ktor-server-sessions:3.3.2")
    implementation("com.ucasoft.ktor:ktor-simple-cache:0.55.3")
    implementation("com.ucasoft.ktor:ktor-simple-redis-cache:0.55.3")
    implementation("com.ucasoft.ktor:ktor-simple-cache:0.55.3")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
