import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "org.zowe"
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        pluginVerifier()
        zipSigner()
    }
    implementation("org.zowe.client.java.sdk:zowe-client-java-sdk:7.0.7")
    implementation("com.konghq:unirest-modules-jackson:4.4.5")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}

intellijPlatform {
    pluginConfiguration {
        id = "org.zowe.explorer.java.sdk"
        name = "Zowe Explorer - Java SDK"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "251"
        }
        description = """
            A clean-room IntelliJ z/OS explorer prototype powered by the Zowe Client Java SDK.
            Includes Jobs, Data Sets, USS browsing/editing, stateful TSO commands, MVS console commands, USS SSH commands, and JobMonitor-based job status monitoring.
        """.trimIndent()
        vendor {
            name = "Zowe Community Prototype"
            url = "https://www.zowe.org"
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<RunIdeTask>("runIde") {
    jvmArgs("-Xmx2g", "-Xms512m", "-XX:+UseG1GC")
}