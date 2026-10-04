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
        id = "com.frankgiordano.zowe.explorer.java"
        name = "Zowe Explorer (Java SDK Edition)"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "251"
        }
        description = """
            Lightweight z/OS Explorer for IntelliJ IDEA powered by the Zowe Client Java SDK.
            Provides Data Set browsing/editing, PDS member CRUD operations, USS browsing, stateful TSO, MVS console commands, and Job monitoring.
        """.trimIndent()
        vendor {
            name = "Frank Giordano"
            url = "https://github.com/frankgiordano/zowe-explorer-java-intellij-sdk"
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
