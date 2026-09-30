# Zowe Explorer - Java SDK (IntelliJ prototype)

Clean-room IntelliJ plugin prototype powered by **Zowe Client Java SDK 7.0.7**.

## Included explorer areas

### Jobs
- List jobs with `JobGet`
- Submit JCL data sets with `JobSubmit`
- Monitor jobs to OUTPUT with **`JobMonitor.waitByOutputStatus`**
- Load JCL
- Load spool file metadata and spool content

### Data Sets
- Search data sets with `DsnList`
- Expand PDS/PDSE members with `DsnList.getMembers`
- Open sequential data sets and members with `DsnGet` in normal IntelliJ editor tabs
- Save editor changes back to z/OS with `DsnWrite` using **Ctrl+S / Save All**

### USS
- List directories/files with `UssList`
- Navigate directories
- Open text files with `UssGet` in normal IntelliJ editor tabs
- Detect the IntelliJ file type from USS filenames for syntax-aware editing where supported
- Save editor changes back to z/OS with `UssWrite` using **Ctrl+S / Save All**

### Commands
- **Stateful TSO terminal** using `TsoStart`, `TsoCmd.issueCommandByTsoSessionId`, and `TsoStop`
- Reuses one TSO address space across multiple commands until explicitly stopped
- **MVS Console** command execution with `ConsoleCmd`
- **USS SSH** command execution with `UssCmd`
- SSH host-key verification uses the SDK default `~/.ssh/known_hosts` behavior
- Configurable TSO account, SSH port, and SSH timeout in the Connection dialog

## Architecture

```text
IntelliJ UI
  |
  +-- JobService -------- JobGet / JobSubmit / JobMonitor
  +-- DataSetService ---- DsnList / DsnGet / DsnWrite
  +-- UssService -------- UssList / UssGet / UssWrite
  +-- CommandService ----- TsoStart / TsoCmd / TsoStop / ConsoleCmd / UssCmd
  +-- RemoteEditorManager
       +-- LightVirtualFile / FileEditorManager
       +-- Ctrl+S / Save All -> DsnWrite or UssWrite
  |
Zowe Client Java SDK 7.0.7
  |
z/OSMF
```

Credentials are stored using IntelliJ `PasswordSafe`; the password is not written into project files.

## Run in a sandbox IntelliJ

Open this project in IntelliJ IDEA, use JDK 21 as the Gradle JVM, then run the shared **Run Zowe Explorer** configuration (Gradle `runIde`).

Or from the project root with Gradle 8.x installed:

```bash
gradle runIde
```

> Note: this source ZIP does not bundle a Gradle wrapper binary. IntelliJ can import/run the Gradle project directly, or you can generate a wrapper locally with `gradle wrapper`.

In the sandbox IDE:

1. Open **View -> Tool Windows -> Zowe Java Explorer**.
2. Click **Connection** and enter z/OSMF host, port, user, and password.
3. Use the **Jobs**, **Data Sets**, **USS**, and **Commands** tabs.
4. Double-click a data set/member or USS file to open it in the main IntelliJ editor.
5. Edit normally and use **Ctrl+S** or **Save All** to write the contents back to z/OS.

## Build an installable plugin ZIP

```bash
gradle buildPlugin
```

The plugin ZIP is generated under:

```text
build/distributions/
```

Install it from IntelliJ with **Settings -> Plugins -> gear -> Install Plugin from Disk...**.

## Prototype status

This is not yet a full feature-for-feature replacement for Zowe Explorer IntelliJ. The current goal is to prove a consolidated architecture in which the IntelliJ UI remains independent while all z/OS service access goes through the Zowe Client Java SDK.

Good next additions include favorites/persistence, create/delete/rename/copy actions, richer job filtering, z/OS logs, workflows, variables, progress indicators/cancellation, TeamConfig profile import, automated tests, and richer conflict-resolution UX.


## Remote editor behavior

Data set members and USS files are represented by IntelliJ `LightVirtualFile` instances and opened through `FileEditorManager`. A `FileDocumentManagerListener` detects normal IDE save operations and delegates persistence to `DsnWrite` or `UssWrite` on a pooled background thread.

### Safe remote save / conflict protection

Before every remote write, the plugin re-reads the current data set/member or USS file and compares it with the baseline captured when the editor was opened or last successfully saved.

If the mainframe copy changed independently, the plugin blocks the write and prompts with:

- **Overwrite Remote** — explicitly replace the newer z/OS content with the editor content
- **Reload Remote** — discard the editor version and load the latest z/OS content
- **Cancel** — leave the remote resource untouched

This is content-based optimistic concurrency, so it does not require an ETag/timestamp API from the Java SDK. Remote writes remain asynchronous; network/write failures are reported after IntelliJ's local save event.
