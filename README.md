
# Zowe Explorer (Java SDK Edition)

Lightweight z/OS Explorer plugin for IntelliJ IDEA powered by **Zowe Client Java SDK 7.0.7**.

---

## Features & Capabilities

### 🔌 Multi-Connection Profile Management
- **Multiple Connection Profiles**: Create, edit, duplicate, and remove connection profiles (`ConnectionProfile` storing Host, Port, User, SSH Port, SSH Timeout, and TSO Account).
- **Header Connection Selector**: Switch active z/OS connection profiles on the fly directly from the tool window header via a dropdown combo box.
- **Asynchronous Test Connection**: Verify z/OSMF credentials and connection settings asynchronously from the **Connection Manager** dialog (`ConnectionDialog`).
- **Secure Credentials**: Passwords are saved securely using IntelliJ's `PasswordSafe` service.
- **Seamless Migration**: Automatic background migration of legacy single-connection configurations to profile-based storage.

### 📁 Data Sets & PDS Member Management
- **Search & Mask History**: Search data sets with `DsnList`. Save up to 20 recent search masks persistently across IDE restarts in an editable combobox. Context menu and trash action allow deleting individual mask entries or clearing history.
- **Tree Node Visuals**: Clear node icons distinguishing PDS/PDSE folders (`AllIcons.Nodes.Folder`), Sequential (PS) files (`AllIcons.FileTypes.Text`), and PDS Members (`AllIcons.Nodes.C_public`).
- **Double-Click Workflows**:
    - **PDS / PDSE**: Expands members directly under the dataset tree node.
    - **Sequential (PS)**: Opens the dataset directly in an IntelliJ editor tab.
    - **Non-Sequential / Non-PDS**: Intercepts un-supported types with informative warning dialogs.
- **PDS Member CRUD Operations**:
    - **Create Member**: Right-click PDS node -> `Create Member...` with full z/OS member name validation (1–8 characters, uppercase/alphanumeric and national characters `@`, `#`, `$`). Uses `DsnWrite`.
    - **Delete Member**: Right-click member node -> `Delete...` with confirmation prompt using `DsnDelete`.
    - **Rename Member**: Right-click member node -> `Rename...` with input dialog using `DsnUpdate`.
    - **Refresh Members**: Right-click PDS node -> `Refresh Members`.
- **ISPF Member Metadata**: Formatted line-by-line key-value metadata display (Member, Version, Mod Level, Created, Modified, Current/Initial lines, User ID, SCLM, etc.).
- **Archived Dataset Handling**: Gracefully handles CA Disk / DFHSM / TSO recall errors (`isArchivedError` / `isArchivedDataset`) with a **"Data Set Archived"** warning dialog and missing DSORG fallback.

### 📂 Unix System Services (USS)
- **Directory Browsing**: Navigate directories and inspect file structures with `UssList`.
- **Remote File Editing**: Open USS text files with `UssGet` in normal IntelliJ editor tabs with automatic syntax highlighting.
- **Remote Save**: Write edits back to z/OS using `UssWrite` on `Ctrl+S` / `Save All`.

### ⚙️ Jobs Management
- **List & Inspect Jobs**: Fetch z/OS jobs via `JobGet`.
- **Submit JCL**: Submit JCL data sets directly to JES using `JobSubmit`.
- **Real-Time Job Monitoring**: Track jobs until `OUTPUT` status using `JobMonitor.waitByOutputStatus`.
- **Spool Output**: View spool file metadata, view individual spool outputs, and save spool files to disk.

### 💻 Commands & Terminals
- **Stateful TSO Terminal**: Issue stateful TSO commands (`TsoStart`, `TsoCmd.issueCommandByTsoSessionId`, `TsoStop`) reusing a single TSO address space across commands.
- **MVS Console**: Execute MVS console commands using `ConsoleCmd`.
- **USS SSH Terminal**: Execute remote SSH commands on z/OS Unix System Services using `UssCmd` with host-key verification (`~/.ssh/known_hosts`).

### 💾 Remote Editor Integration & Concurrency Protection
- **Global Save Interception**: Listens to IntelliJ save actions (`Ctrl+S`, `Save All`, File menu save) and application bus events (`FileDocumentManagerListener`) to write back modified remote editor tabs (`LightVirtualFile`).
- **Auto-Save on Tab Close**: Saves modified remote editors when closing tabs.
- **Optimistic Concurrency & Conflict Protection**: Re-reads remote files prior to writing and compares content against the initial baseline. Prompts user with **Overwrite Remote**, **Reload Remote**, or **Cancel** if mainframe content changed concurrently.

---
  
## Architecture

```text
IntelliJ UI (Zowe Tool Window)
  │
  ├── Connection Profile Manager (PasswordSafe & Persistence)
  ├── JobService -------- JobGet / JobSubmit / JobMonitor
  ├── DataSetService ---- DsnList / DsnGet / DsnWrite / DsnDelete / DsnUpdate
  ├── UssService -------- UssList / UssGet / UssWrite
  ├── CommandService ---- TsoStart / TsoCmd / TsoStop / ConsoleCmd / UssCmd
  └── RemoteEditorManager
       ├── LightVirtualFile / FileEditorManager
       └── Action & Document Save Listeners -> DsnWrite / UssWrite
  │
Zowe Client Java SDK 7.0.7
  │
z/OSMF / z/OS
```

---

## Development & Building

### Prerequisites
- Java 21 JDK
- IntelliJ IDEA (2025.1 or newer)

### Run in Sandbox IDE
Run the IDE sandbox using Gradle:

```bash
gradlew runIde
```

In the sandbox IDE:
1. Open **View -> Tool Windows -> Zowe Java Explorer**.
2. Click **Manage...** or **Connection** to set up a z/OS connection profile (z/OSMF host, port, credentials).
3. Click **Test Connection** to verify settings.
4. Browse **Data Sets**, **USS**, **Jobs**, and execute **Commands**.
5. Double-click a data set/member or USS file to open it in the main IntelliJ editor. Alternatively, right-click the selected item and choose Open.
6. Edit normally and use **Ctrl+S** or **Save All** to write the contents back to z/OS.

### Build Installable Plugin Package

```bash
gradlew buildPlugin
```

The compiled plugin package ZIP will be located in:

```text
build/distributions/com.frankgiordano.zowe.explorer.java-1.0.0.zip
```

To install locally in IntelliJ:
1. Open **Settings / Preferences -> Plugins**.
2. Click the gear icon ⚙️ and select **Install Plugin from Disk...**.
3. Select the generated `.zip` file from `build/distributions/`.

---

## 🚀 Publishing to JetBrains Marketplace

### Option A: Manual Web Upload (Recommended for First Release)

1. **Build the Plugin Distribution**:
   ```bash
   gradlew buildPlugin
   ```
   Verify that the output package `build/distributions/com.frankgiordano.zowe.explorer.java-1.0.0.zip` exists.

2. **Verify Plugin Compatibility**:
   ```bash
   gradlew verifyPlugin
   ```

3. **Log in to JetBrains Marketplace**:
   Go to [JetBrains Marketplace Publisher Portal](https://plugins.jetbrains.com/). Log in with your JetBrains Account.

4. **Upload New Plugin**:
    - Click **Add Plugin** -> **Upload Plugin**.
    - Drag and drop or upload the ZIP file from `build/distributions/`.
    - Select license terms and complete the verification details.
    - JetBrains will perform automated security and compatibility verification. Once approved, your plugin will be available publicly in the Marketplace.

---

### Option B: Automated Publishing via Gradle

You can publish directly from the command line or CI/CD pipelines using the JetBrains Marketplace Publisher API token.

1. **Generate Publisher Token**:
    - Log in to [plugins.jetbrains.com](https://plugins.jetbrains.com/).
    - Click your profile picture -> **My Tokens**.
    - Click **Generate Token** and copy the token value.

2. **Configure Token**:
   Set the token as an environment variable or Gradle property:
   ```bash
   export PUBLISH_TOKEN="perm:your_jetbrains_marketplace_token"
   ```

3. **Publish Command**:
   ```bash
   gradlew publishPlugin -PintellijPlatform.publishing.token=$env:PUBLISH_TOKEN
   ```

---

## License & Credits

Powered by [Zowe Client Java SDK](https://github.com/zowe/zowe-client-java-sdk). Developed by Frank Giordano.
