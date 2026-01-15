import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.compose") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "dev.ru.ghelid"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Ktor HTTP Client
    implementation("io.ktor:ktor-client-core:3.0.2")
    implementation("io.ktor:ktor-client-cio:3.0.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.2")
    implementation("io.ktor:ktor-client-logging:3.0.2")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "AI Chat"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "AI Chat"
                upgradeUuid = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

// Custom task to run MCP test client
tasks.register<JavaExec>("testMcp") {
    mainClass.set("test.McpClientTestKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

// Custom task to run MCP Shell Command Server
tasks.register<JavaExec>("runMcpShellServer") {
    mainClass.set("mcp.ShellCommandMcpServerKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

// Custom task to run MCP Git Server
tasks.register<JavaExec>("runMcpGitServer") {
    mainClass.set("mcp.GitMcpServerKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

// Custom task to run MCP Project Documentation Server
tasks.register<JavaExec>("runMcpProjectDocsServer") {
    mainClass.set("mcp.ProjectDocsMcpServerKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

// Custom task to run MCP Issue Tickets Server
tasks.register<JavaExec>("runMcpIssueTicketsServer") {
    mainClass.set("mcp.IssueTicketsMcpServerKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}

// Custom task to run MCP Task Board Server
tasks.register<JavaExec>("runMcpTaskBoardServer") {
    mainClass.set("mcp.TaskBoardMcpServerKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    standardOutput = System.out
    errorOutput = System.err
}