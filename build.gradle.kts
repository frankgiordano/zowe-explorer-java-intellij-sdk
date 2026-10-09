import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
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
            <h3>Lightweight z/OS Explorer for IntelliJ IDEA powered by Zowe Client Java SDK</h3>
            <p>Connect seamlessly to IBM z/OS mainframes directly from IntelliJ IDEA to manage Data Sets, Unix System Services (USS) files, JES Jobs, TSO commands, and MVS consoles.</p>

            <h4>Key Features</h4>
            <ul>
                <li><b>Multi-Connection Profiles</b>: Manage multiple z/OSMF connection profiles with secure credential storage via IntelliJ PasswordSafe.</li>
                <li><b>Data Sets & PDS Members</b>: Search data sets with persistent mask history. Browse, create, edit, rename, and delete PDS/PDSE members and Sequential (PS) files.</li>
                <li><b>Unix System Services (USS)</b>: Full file tree browser with smart sorting (directories first), real-time live filtering (wildcards like <code>*.sh</code>), right-click file/directory CRUD actions, and lenient handling for ASCII control characters.</li>
                <li><b>Remote Editor Integration</b>: Edit remote Data Sets and USS text files directly in native IntelliJ editor tabs with automatic syntax highlighting and <code>Ctrl+S</code> writeback. Includes optimistic concurrency protection against remote mainframe changes.</li>
                <li><b>JES Job Management</b>: Monitor jobs, view spool outputs, submit JCL data sets, and download spool files.</li>
                <li><b>Interactive Commands</b>: Stateful TSO terminal session, MVS console commands, and remote USS SSH execution.</li>
            </ul>
        """.trimIndent()
        changeNotes = """
            <h4>v1.0.1</h4>
            <ul>
                <li><b>Change Tag (chtag)</b>: New USS right-click action to set file tags (ISO8859-1, UTF-8, IBM-1047, IBM-037, binary, remove, or custom code set) via <code>UssChangeTag</code>. Resolves ASCII files displaying as unreadable characters due to z/OSMF assuming untagged files are EBCDIC.</li>
                <li><b>Open With Encoding</b>: New USS right-click action to read and edit files using an explicit encoding without altering file tags on z/OS or requiring write permissions. Reopening under a different encoding replaces the stale editor tab cleanly.</li>
                <li><b>Persistent USS Path History</b>: Converted USS Path field into an editable dropdown combo box saving up to 20 recently listed directory paths across IDE restarts, complete with delete button and context menu history management.</li>
                <li><b>Responsive UI & Component Wrapping</b>: Added custom <code>WrapLayout</code> for toolbars across all tabs (Jobs, Data Sets, USS, Commands, Connection Manager) so control buttons and input fields automatically wrap to new rows when tool window panes or splitters are narrowed.</li>
                <li><b>Detail View Line Wrapping & Tree Tooltips</b>: Enabled word wrapping in all detail text areas and added hover tooltips for tree nodes in Jobs, Data Sets, and USS trees when item names are truncated.</li>
            </ul>
            <h4>v1.0.0 Initial Release</h4>
            <ul>
                <li><b>USS Enhancements</b>: Live search/filtering, tree node sorting (directories first), right-click file/directory CRUD (Create, Rename, Delete), and lenient JSON parsing for control characters.</li>
                <li><b>Data Set Operations</b>: Search data sets with persistent mask history, view ISPF member metadata, and edit remote members with conflict protection.</li>
                <li><b>Multi-Profile Manager</b>: Connection profile manager with secure password storage and header profile selector.</li>
                <li><b>Jobs & Commands</b>: Real-time JES job monitoring, spool viewer, stateful TSO address space, MVS console, and USS SSH.</li>
            </ul>
        """.trimIndent()
        vendor {
            name = "Frank Giordano"
            url = "https://github.com/frankgiordano/zowe-explorer-java-intellij-sdk"
        }
    }
    pluginVerification {
        ides {
            recommended()
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
