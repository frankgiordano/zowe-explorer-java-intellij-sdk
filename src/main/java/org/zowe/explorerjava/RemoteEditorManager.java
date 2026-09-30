package org.zowe.explorerjava;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Opens remote z/OS resources as normal IntelliJ editor tabs and writes them back
 * when IntelliJ saves the corresponding document (Ctrl+S / Save All).
 *
 * Before each remote write, the current z/OS content is fetched and compared with
 * the snapshot captured after the last successful open/save. If the remote content
 * changed independently, the user must explicitly choose whether to overwrite,
 * reload the remote version, or leave the remote resource unchanged.
 */
public final class RemoteEditorManager implements Disposable {

    private final Project project;
    private final Map<RemoteVirtualFile, RemoteResource> resources = new ConcurrentHashMap<>();

    public RemoteEditorManager(Project project) {
        this.project = project;
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
                    @Override
                    public void beforeDocumentSaving(@NotNull Document document) {
                        var virtualFile = FileDocumentManager.getInstance().getFile(document);
                        if (virtualFile instanceof RemoteVirtualFile remoteFile) {
                            RemoteResource resource = resources.get(remoteFile);
                            if (resource != null) {
                                safeSaveAsync(resource, document, document.getText());
                            }
                        }
                    }
                });
    }

    public void openDataSet(String target, String content) {
        open(RemoteResource.dataSet(target, content), content);
    }

    public void openUss(String path, String content) {
        open(RemoteResource.uss(path, content), content);
    }

    private void open(RemoteResource resource, String content) {
        // Reuse an existing tab for the same remote target where possible.
        for (Map.Entry<RemoteVirtualFile, RemoteResource> entry : resources.entrySet()) {
            if (entry.getValue().sameTarget(resource)) {
                FileEditorManager.getInstance(project).openFile(entry.getKey(), true, true);
                return;
            }
        }

        String displayName = resource.displayName();
        RemoteVirtualFile file = new RemoteVirtualFile(displayName, content, resource);
        resources.put(file, resource);
        FileEditorManager.getInstance(project).openFile(file, true, true);
    }

    private void safeSaveAsync(RemoteResource resource, Document document, String localContent) {
        if (!resource.saveInProgress.compareAndSet(false, true)) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String remoteContent = readRemote(resource);
                String baseline = resource.baselineContent;

                // No independent remote change since the editor was opened / last saved.
                if (Objects.equals(remoteContent, baseline)) {
                    if (!Objects.equals(localContent, remoteContent)) {
                        writeRemote(resource, localContent);
                    }
                    resource.baselineContent = localContent;
                    publishSaved(resource.target);
                    return;
                }

                // Remote changed, but local already matches it. Just adopt the new baseline.
                if (Objects.equals(localContent, remoteContent)) {
                    resource.baselineContent = remoteContent;
                    publishSaved(resource.target);
                    return;
                }

                // True conflict: baseline, local editor, and current z/OS content differ.
                int choice = askConflictResolution(resource.target);
                if (choice == 0) { // Overwrite remote
                    writeRemote(resource, localContent);
                    resource.baselineContent = localContent;
                    publishSaved(resource.target);
                } else if (choice == 1) { // Reload remote
                    resource.baselineContent = remoteContent;
                    ApplicationManager.getApplication().invokeLater(() ->
                            WriteCommandAction.runWriteCommandAction(project, () -> document.setText(remoteContent)));
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showInfoMessage(project,
                                    "Reloaded the latest z/OS content for " + resource.target + ".",
                                    "Zowe Java Explorer"));
                } else {
                    ApplicationManager.getApplication().invokeLater(() ->
                            Messages.showWarningDialog(project,
                                    "The remote resource was not changed. Your editor still contains the local version. " +
                                            "Modify the document and save again when you are ready to resolve the conflict.",
                                    "Remote Save Cancelled"));
                }
            } catch (Exception ex) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog(project,
                                "Could not safely save " + resource.target + " to z/OS.\n\n" + ex,
                                "Zowe Java Explorer"));
            } finally {
                resource.saveInProgress.set(false);
            }
        });
    }

    private int askConflictResolution(String target) {
        AtomicInteger result = new AtomicInteger(2);
        ApplicationManager.getApplication().invokeAndWait(() -> result.set(
                Messages.showDialog(project,
                        "The z/OS resource changed after you opened it:\n\n" + target +
                                "\n\nChoose how to resolve the conflict.",
                        "Remote Change Detected",
                        new String[]{"Overwrite Remote", "Reload Remote", "Cancel"},
                        2,
                        Messages.getWarningIcon())
        ));
        return result.get();
    }

    private String readRemote(RemoteResource resource) throws Exception {
        if (resource.kind == RemoteKind.DATA_SET) {
            return new DataSetService(ZoweConnectionProvider.current()).read(resource.target);
        }
        return new UssService(ZoweConnectionProvider.current()).readText(resource.target);
    }

    private void writeRemote(RemoteResource resource, String content) throws Exception {
        if (resource.kind == RemoteKind.DATA_SET) {
            new DataSetService(ZoweConnectionProvider.current()).write(resource.target, content);
        } else {
            new UssService(ZoweConnectionProvider.current()).writeText(resource.target, content);
        }
    }

    private void publishSaved(String target) {
        ApplicationManager.getApplication().invokeLater(() ->
                project.getMessageBus().syncPublisher(RemoteSaveListener.TOPIC).remoteSaved(target));
    }

    @Override
    public void dispose() {
        resources.clear();
    }

    private static final class RemoteVirtualFile extends LightVirtualFile {
        private final RemoteResource resource;

        private RemoteVirtualFile(String name, String content, RemoteResource resource) {
            super(name, fileTypeFor(name, resource), content);
            this.resource = resource;
            setWritable(true);
        }

        private static FileType fileTypeFor(String name, RemoteResource resource) {
            if (resource.kind == RemoteKind.USS) {
                FileType detected = FileTypeManager.getInstance().getFileTypeByFileName(name);
                if (!detected.isBinary()) {
                    return detected;
                }
            }

            return PlainTextFileType.INSTANCE;
        }

        @Override
        public @NotNull String getPresentableName() {
            return resource.displayName();
        }

        public String getRemotePresentableUrl() {
            return resource.presentableUrl();
        }
    }

    private enum RemoteKind { DATA_SET, USS }

    private static final class RemoteResource {
        private final RemoteKind kind;
        private final String target;
        private final AtomicBoolean saveInProgress = new AtomicBoolean(false);
        private volatile String baselineContent;

        private RemoteResource(RemoteKind kind, String target, String baselineContent) {
            this.kind = kind;
            this.target = target;
            this.baselineContent = baselineContent;
        }

        static RemoteResource dataSet(String target, String baselineContent) {
            return new RemoteResource(RemoteKind.DATA_SET, target, baselineContent);
        }

        static RemoteResource uss(String target, String baselineContent) {
            return new RemoteResource(RemoteKind.USS, target, baselineContent);
        }

        boolean sameTarget(RemoteResource other) {
            return kind == other.kind && target.equals(other.target);
        }

        String displayName() {
            if (kind == RemoteKind.USS) {
                int slash = target.lastIndexOf('/');
                return slash >= 0 && slash < target.length() - 1 ? target.substring(slash + 1) : target;
            }
            return target;
        }

        String presentableUrl() {
            return kind == RemoteKind.DATA_SET ? "zowe-dsn://" + target : "zowe-uss://" + target;
        }
    }
}
