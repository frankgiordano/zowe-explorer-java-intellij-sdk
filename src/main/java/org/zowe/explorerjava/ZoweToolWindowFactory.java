package org.zowe.explorerjava;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import zowe.client.sdk.zosfiles.dsn.model.Dataset;
import zowe.client.sdk.zosfiles.dsn.model.Member;
import zowe.client.sdk.zosfiles.uss.model.UnixFile;
import zowe.client.sdk.zosjobs.model.Job;
import zowe.client.sdk.zosjobs.model.JobFile;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

/**
 * Main Zowe Explorer tool window.
 *
 * UI code talks only to Explorer service facades. z/OSMF access is delegated to the
 * Zowe Client Java SDK through JobService, DataSetService, and UssService.
 */
public final class ZoweToolWindowFactory implements ToolWindowFactory {

    private final JBLabel globalState =
            new JBLabel("Configure a z/OSMF connection to begin.");

    // Jobs
    private final DefaultMutableTreeNode jobsRoot =
            new DefaultMutableTreeNode("Jobs");
    private final DefaultTreeModel jobsModel =
            new DefaultTreeModel(jobsRoot);
    private final JTree jobsTree =
            new JTree(jobsModel);
    private final JBTextArea jobDetails =
            textArea(false);

    /*
     * Job search filters.
     *
     * Owner defaults to the active z/OSMF connection user.
     * Prefix defaults to "*" so that all jobs for that owner are displayed.
     */
    private final JBTextField jobOwner = new JBTextField();
    private final JBTextField jobPrefix = new JBTextField("*");

    // Data sets
    private final DefaultMutableTreeNode dsnRoot =
            new DefaultMutableTreeNode("Data Sets");
    private final DefaultTreeModel dsnModel =
            new DefaultTreeModel(dsnRoot);
    private final JTree dsnTree =
            new JTree(dsnModel);
    private final JBTextArea dsnDetails =
            textArea(false);
    private final JBTextField dsnMask =
            new JBTextField();

    // USS
    private final DefaultMutableTreeNode ussRoot =
            new DefaultMutableTreeNode("USS");
    private final DefaultTreeModel ussModel =
            new DefaultTreeModel(ussRoot);
    private final JTree ussTree =
            new JTree(ussModel);
    private final JBTextArea ussDetails =
            textArea(false);
    private final JBTextField ussPath =
            new JBTextField("/");

    // Commands
    private final JBTextArea tsoOutput =
            textArea(false);
    private final JBTextField tsoInput =
            new JBTextField();
    private final JBLabel tsoSessionState =
            new JBLabel("TSO session: stopped");

    private final JBTextArea consoleOutput =
            textArea(false);
    private final JBTextField consoleInput =
            new JBTextField();

    private final JBTextArea sshOutput =
            textArea(false);
    private final JBTextField sshInput =
            new JBTextField();

    private RemoteEditorManager remoteEditors;
    private volatile CommandService commandService;

    @Override
    public void createToolWindowContent(
            @NotNull Project project,
            @NotNull ToolWindow toolWindow) {

        remoteEditors = new RemoteEditorManager(project);

        Disposer.register(project, remoteEditors);

        Disposer.register(project, () -> {
            CommandService service = commandService;
            if (service != null) {
                service.close();
            }
        });

        project.getMessageBus()
                .connect(project)
                .subscribe(
                        RemoteSaveListener.TOPIC,
                        (RemoteSaveListener) target ->
                                setState("Saved " + target + " to z/OS.")
                );

        /*
         * Initialize the Jobs owner filter from the currently configured
         * z/OSMF connection.
         */
        resetJobFilters();

        JPanel panel =
                new JPanel(new BorderLayout(6, 6));

        panel.add(
                createGlobalToolbar(project),
                BorderLayout.NORTH);

        JTabbedPane tabs =
                new JTabbedPane();

        tabs.addTab(
                "Jobs",
                createJobsPanel(project));

        tabs.addTab(
                "Data Sets",
                createDataSetsPanel(project));

        tabs.addTab(
                "USS",
                createUssPanel(project));

        tabs.addTab(
                "Commands",
                createCommandsPanel(project));

        panel.add(
                tabs,
                BorderLayout.CENTER);

        panel.add(
                globalState,
                BorderLayout.SOUTH);

        Content content =
                ContentFactory.getInstance()
                        .createContent(
                                panel,
                                "Explorer",
                                false);

        toolWindow.getContentManager()
                .addContent(content);
    }

    // -------------------------------------------------------------------------
    // Global connection toolbar
    // -------------------------------------------------------------------------

    private JComponent createGlobalToolbar(Project project) {

        JPanel toolbar =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JButton connection =
                new JButton("Connection");

        connection.addActionListener(e -> {

            if (new ConnectionDialog().showAndGet()) {

                CommandService old =
                        commandService;

                commandService = null;

                if (old != null) {
                    ApplicationManager
                            .getApplication()
                            .executeOnPooledThread(old::close);
                }

                tsoSessionState.setText(
                        "TSO session: stopped");

                /*
                 * A connection change also resets the Jobs filters.
                 *
                 * The owner is always initialized from the currently
                 * configured connection user.
                 */
                resetJobFilters();

                ZoweConnectionSettings settings =
                        ZoweConnectionSettings.getInstance();

                globalState.setText(
                        "Connection saved for "
                                + settings.getHost()
                                + " as "
                                + settings.getUser());

                /*
                 * Refresh the Jobs view immediately using the new
                 * connection/user.
                 */
                refreshJobs(project);
            }
        });

        toolbar.add(connection);

        return toolbar;
    }

    // -------------------------------------------------------------------------
    // Jobs
    // -------------------------------------------------------------------------

    private JComponent createJobsPanel(Project project) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                4,
                                4));

        /*
         * Use two toolbar rows.
         *
         * Row 1 = search/filter controls
         * Row 2 = job operations
         *
         * This prevents the Jobs toolbar from becoming excessively wide.
         */
        JPanel north =
                new JPanel();

        north.setLayout(
                new BoxLayout(
                        north,
                        BoxLayout.Y_AXIS));

        JPanel filterToolbar =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JPanel actionToolbar =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        jobOwner.setColumns(10);
        jobPrefix.setColumns(10);

        jobOwner.setToolTipText(
                "Job owner. Defaults to the active connection user. "
                        + "Use * to search all owners you are authorized to view.");

        jobPrefix.setToolTipText(
                "Job name/prefix, for example * or MYJOB*.");

        JButton refresh =
                new JButton("Refresh");

        JButton submit =
                new JButton("Submit JCL DSN");

        JButton monitor =
                new JButton("Monitor to OUTPUT");

        JButton jcl =
                new JButton("Show JCL");

        JButton spool =
                new JButton("Load Spool Files");

        JButton spoolContent =
                new JButton("Open Spool");

        refresh.addActionListener(
                e -> refreshJobs(project));

        /*
         * Pressing Enter in either filter performs a refresh.
         */
        jobOwner.addActionListener(
                e -> refreshJobs(project));

        jobPrefix.addActionListener(
                e -> refreshJobs(project));

        submit.addActionListener(
                e -> submit(project));

        monitor.addActionListener(
                e -> monitorSelected(project));

        jcl.addActionListener(
                e -> loadJcl(project));

        spool.addActionListener(
                e -> loadSpoolFiles(project));

        spoolContent.addActionListener(
                e -> loadSelectedSpool(project));

        filterToolbar.add(
                new JBLabel("Owner:"));

        filterToolbar.add(jobOwner);

        filterToolbar.add(
                new JBLabel("Job:"));

        filterToolbar.add(jobPrefix);

        filterToolbar.add(refresh);

        actionToolbar.add(submit);
        actionToolbar.add(monitor);
        actionToolbar.add(jcl);
        actionToolbar.add(spool);
        actionToolbar.add(spoolContent);

        north.add(filterToolbar);
        north.add(actionToolbar);

        jobsTree.setRootVisible(true);

        jobsTree.addTreeSelectionListener(
                e -> showSelectedJobNode());

        JBSplitter splitter =
                new JBSplitter(
                        false,
                        0.35f);

        splitter.setFirstComponent(
                new JBScrollPane(jobsTree));

        splitter.setSecondComponent(
                new JBScrollPane(jobDetails));

        panel.add(
                north,
                BorderLayout.NORTH);

        panel.add(
                splitter,
                BorderLayout.CENTER);

        return panel;
    }

    /**
     * Reset Jobs filters to defaults associated with the current connection.
     *
     * Owner defaults to the current z/OS user.
     * Job prefix defaults to "*".
     */
    private void resetJobFilters() {

        String user =
                ZoweConnectionSettings
                        .getInstance()
                        .getUser();

        if (user == null) {
            user = "";
        }

        jobOwner.setText(
                user.trim()
                        .toUpperCase(Locale.ROOT));

        jobPrefix.setText("*");
    }

    /**
     * Refresh Jobs using the current Owner and Job prefix filters.
     */
    private void refreshJobs(Project project) {

        String owner =
                jobOwner.getText() == null
                        ? ""
                        : jobOwner.getText().trim();

        String prefix =
                jobPrefix.getText() == null
                        ? ""
                        : jobPrefix.getText().trim();

        /*
         * Blank fields are interpreted as wildcard searches.
         */
        if (owner.isEmpty()) {
            owner = "*";
        }

        if (prefix.isEmpty()) {
            prefix = "*";
        }

        owner =
                owner.toUpperCase(Locale.ROOT);

        prefix =
                prefix.toUpperCase(Locale.ROOT);

        /*
         * Reflect normalized values back into the UI.
         */
        jobOwner.setText(owner);
        jobPrefix.setText(prefix);

        final String requestedOwner =
                owner;

        final String requestedPrefix =
                prefix;

        setState(
                "Loading jobs for owner "
                        + requestedOwner
                        + ", job "
                        + requestedPrefix
                        + "...");

        runBackground(project, () -> {

            List<Job> jobs =
                    new JobService(
                            ZoweConnectionProvider.current())
                            .list(
                                    requestedOwner,
                                    requestedPrefix);

            SwingUtilities.invokeLater(() -> {

                jobsRoot.removeAllChildren();

                for (Job job : jobs) {

                    jobsRoot.add(
                            new DefaultMutableTreeNode(
                                    new JobNode(job)));
                }

                jobsModel.reload();

                jobsTree.expandRow(0);

                setState(
                        "Loaded "
                                + jobs.size()
                                + " jobs for owner "
                                + requestedOwner
                                + ".");
            });
        });
    }

    /**
     * Manual Jobs-tab submit dialog.
     */
    private void submit(Project project) {

        String dsn =
                Messages.showInputDialog(
                        project,
                        "Data set containing JCL "
                                + "(for example USER.JCL(MYJOB))",
                        "Submit z/OS Job",
                        null);

        if (dsn == null
                || dsn.isBlank()) {

            return;
        }

        submitDataSetAsJob(
                project,
                dsn.trim());
    }

    /**
     * Common JCL submission method used by both:
     *
     * 1. Jobs -> Submit JCL DSN
     * 2. Data Sets -> member right-click -> Submit as Job
     */
    private void submitDataSetAsJob(
            Project project,
            String dataSet) {

        setState(
                "Submitting "
                        + dataSet
                        + "...");

        runBackground(project, () -> {

            Job submitted =
                    new JobService(
                            ZoweConnectionProvider.current())
                            .submitDataSet(dataSet);

            SwingUtilities.invokeLater(() -> {

                /*
                 * Display the submitted job immediately in the details panel.
                 */
                jobDetails.setText(
                        formatJob(submitted));

                jobDetails.setCaretPosition(0);

                setState(
                        "Submitted "
                                + dataSet
                                + " as "
                                + submitted.getJobName()
                                + " / "
                                + submitted.getJobId());

                /*
                 * Refresh using the current owner/job filters.
                 *
                 * Normally the submitted job will appear immediately because
                 * Owner defaults to the logged-in user.
                 */
                refreshJobs(project);
            });
        });
    }

    private void monitorSelected(Project project) {

        Job job =
                selectedJob(project);

        if (job == null) {
            return;
        }

        setState(
                "JobMonitor: waiting for "
                        + job.getJobName()
                        + " / "
                        + job.getJobId()
                        + " to reach OUTPUT...");

        runBackground(project, () -> {

            Job completed =
                    new JobService(
                            ZoweConnectionProvider.current())
                            .waitForOutput(
                                    job.getJobName(),
                                    job.getJobId());

            SwingUtilities.invokeLater(() -> {

                jobDetails.setText(
                        formatJob(completed));

                setState(
                        "JobMonitor completed: "
                                + completed.getJobName()
                                + " / "
                                + completed.getJobId());

                refreshJobs(project);
            });
        });
    }

    private void loadJcl(Project project) {

        Job job =
                selectedJob(project);

        if (job == null) {
            return;
        }

        setState(
                "Loading JCL...");

        runBackground(project, () -> {

            String jcl =
                    new JobService(
                            ZoweConnectionProvider.current())
                            .getJcl(job);

            SwingUtilities.invokeLater(() -> {

                jobDetails.setText(jcl);

                jobDetails.setCaretPosition(0);

                setState(
                        "Loaded JCL for "
                                + job.getJobName()
                                + " / "
                                + job.getJobId());
            });
        });
    }

    private void loadSpoolFiles(Project project) {

        Job job =
                selectedJob(project);

        if (job == null) {
            return;
        }

        setState(
                "Loading spool files...");

        runBackground(project, () -> {

            List<JobFile> files =
                    new JobService(
                            ZoweConnectionProvider.current())
                            .getSpoolFiles(job);

            SwingUtilities.invokeLater(() -> {

                DefaultMutableTreeNode jobNode =
                        findJobNode(job);

                if (jobNode != null) {

                    jobNode.removeAllChildren();

                    for (JobFile file : files) {

                        jobNode.add(
                                new DefaultMutableTreeNode(
                                        new SpoolNode(
                                                job,
                                                file)));
                    }

                    jobsModel.reload(jobNode);

                    jobsTree.expandPath(
                            new TreePath(
                                    jobNode.getPath()));
                }

                setState(
                        "Loaded "
                                + files.size()
                                + " spool files.");
            });
        });
    }

    private void loadSelectedSpool(Project project) {

        DefaultMutableTreeNode node =
                selectedNode(jobsTree);

        if (node == null
                || !(node.getUserObject()
                instanceof SpoolNode spool)) {

            Messages.showInfoMessage(
                    project,
                    "Select a spool file first.",
                    "Zowe Java Explorer");

            return;
        }

        setState(
                "Loading spool content...");

        runBackground(project, () -> {

            String content =
                    new JobService(
                            ZoweConnectionProvider.current())
                            .getSpool(spool.file);

            SwingUtilities.invokeLater(() -> {

                jobDetails.setText(content);

                jobDetails.setCaretPosition(0);

                setState(
                        "Loaded spool content.");
            });
        });
    }

    private void showSelectedJobNode() {

        DefaultMutableTreeNode node =
                selectedNode(jobsTree);

        if (node == null) {
            return;
        }

        Object value =
                node.getUserObject();

        if (value instanceof JobNode j) {

            jobDetails.setText(
                    formatJob(j.job));

        } else if (value
                instanceof SpoolNode s) {

            jobDetails.setText(
                    s.file.toString());
        }

        jobDetails.setCaretPosition(0);
    }

    // -------------------------------------------------------------------------
    // Data Sets
    // -------------------------------------------------------------------------

    private JComponent createDataSetsPanel(Project project) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                4,
                                4));

        JPanel toolbar =
                new JPanel(
                        new BorderLayout(
                                4,
                                2));

        JPanel left =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JButton search =
                new JButton("Search");

        JButton members =
                new JButton("Members");

        JButton open =
                new JButton("Open");

        dsnMask.setColumns(26);

        dsnMask.setToolTipText(
                "Data set mask, for example USER.* or USER.JCL");

        search.addActionListener(
                e -> searchDataSets(project));

        members.addActionListener(
                e -> loadMembers(project));

        open.addActionListener(
                e -> openDataSetSelection(project));

        dsnMask.addActionListener(
                e -> searchDataSets(project));

        left.add(
                new JBLabel("Mask:"));

        left.add(dsnMask);
        left.add(search);
        left.add(members);
        left.add(open);

        toolbar.add(
                left,
                BorderLayout.WEST);

        dsnTree.setRootVisible(true);

        dsnTree.addTreeSelectionListener(
                e -> showSelectedDsnNode());

        /*
         * Dataset tree mouse processing.
         *
         * Double-click continues to open the selected resource.
         *
         * Right-click on a PDS/PDSE member provides:
         *
         *   Open
         *   Submit as Job
         */
        dsnTree.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mousePressed(
                            MouseEvent e) {

                        maybeShowDataSetPopup(
                                project,
                                e);
                    }

                    @Override
                    public void mouseReleased(
                            MouseEvent e) {

                        maybeShowDataSetPopup(
                                project,
                                e);
                    }

                    @Override
                    public void mouseClicked(
                            MouseEvent e) {

                        if (e.getClickCount() == 2
                                && SwingUtilities
                                .isLeftMouseButton(e)) {

                            openDataSetSelection(
                                    project);
                        }
                    }
                });

        JBSplitter splitter =
                new JBSplitter(
                        false,
                        0.35f);

        splitter.setFirstComponent(
                new JBScrollPane(dsnTree));

        splitter.setSecondComponent(
                new JBScrollPane(dsnDetails));

        panel.add(
                toolbar,
                BorderLayout.NORTH);

        panel.add(
                splitter,
                BorderLayout.CENTER);

        return panel;
    }

    /**
     * Display the context menu when a PDS/PDSE member is right-clicked.
     */
    private void maybeShowDataSetPopup(
            Project project,
            MouseEvent event) {

        if (!event.isPopupTrigger()) {
            return;
        }

        TreePath path =
                dsnTree.getPathForLocation(
                        event.getX(),
                        event.getY());

        if (path == null) {
            return;
        }

        /*
         * Right-click should also select the node beneath the mouse.
         */
        dsnTree.setSelectionPath(path);

        DefaultMutableTreeNode node =
                (DefaultMutableTreeNode)
                        path.getLastPathComponent();

        Object value =
                node.getUserObject();

        /*
         * Submit-as-job is intentionally exposed only for members.
         *
         * We do not attempt to parse the member looking for a JOB card.
         * JES/z/OSMF remains responsible for validating the JCL.
         */
        if (!(value instanceof MemberNode member)) {
            return;
        }

        JPopupMenu menu =
                new JPopupMenu();

        JMenuItem open =
                new JMenuItem("Open");

        JMenuItem submit =
                new JMenuItem("Submit as Job");

        open.addActionListener(
                e -> openDataSetSelection(project));

        submit.addActionListener(
                e -> submitMemberAsJob(
                        project,
                        member));

        menu.add(open);

        menu.addSeparator();

        menu.add(submit);

        menu.show(
                event.getComponent(),
                event.getX(),
                event.getY());
    }

    /**
     * Submit the selected PDS/PDSE member as JCL.
     *
     * Example:
     *
     *   USER.JCL(MYJOB)
     */
    private void submitMemberAsJob(
            Project project,
            MemberNode member) {

        submitDataSetAsJob(
                project,
                member.qualifiedName());
    }

    private void searchDataSets(Project project) {

        String mask =
                dsnMask.getText().trim();

        if (mask.isEmpty()) {

            Messages.showInfoMessage(
                    project,
                    "Enter a data set mask first.",
                    "Zowe Java Explorer");

            return;
        }

        setState(
                "Searching data sets: "
                        + mask);

        runBackground(project, () -> {

            List<Dataset> items =
                    new DataSetService(
                            ZoweConnectionProvider.current())
                            .list(mask);

            SwingUtilities.invokeLater(() -> {

                dsnRoot.removeAllChildren();

                for (Dataset item : items) {

                    dsnRoot.add(
                            new DefaultMutableTreeNode(
                                    new DatasetNode(item)));
                }

                dsnModel.reload();

                dsnTree.expandRow(0);

                setState(
                        "Found "
                                + items.size()
                                + " data sets.");
            });
        });
    }

    private void loadMembers(Project project) {

        DefaultMutableTreeNode node =
                selectedNode(dsnTree);

        if (node == null
                || !(node.getUserObject()
                instanceof DatasetNode datasetNode)) {

            Messages.showInfoMessage(
                    project,
                    "Select a partitioned data set first.",
                    "Zowe Java Explorer");

            return;
        }

        String dsn =
                datasetNode.dataset
                        .getDsname();

        setState(
                "Loading members for "
                        + dsn
                        + "...");

        runBackground(project, () -> {

            List<Member> members =
                    new DataSetService(
                            ZoweConnectionProvider.current())
                            .members(dsn);

            SwingUtilities.invokeLater(() -> {

                node.removeAllChildren();

                for (Member member : members) {

                    node.add(
                            new DefaultMutableTreeNode(
                                    new MemberNode(
                                            dsn,
                                            member)));
                }

                dsnModel.reload(node);

                dsnTree.expandPath(
                        new TreePath(
                                node.getPath()));

                setState(
                        "Loaded "
                                + members.size()
                                + " members from "
                                + dsn
                                + ".");
            });
        });
    }

    private void openDataSetSelection(
            Project project) {

        DefaultMutableTreeNode node =
                selectedNode(dsnTree);

        if (node == null) {
            return;
        }

        Object value =
                node.getUserObject();

        String target;

        if (value
                instanceof MemberNode member) {

            target =
                    member.qualifiedName();

        } else if (value
                instanceof DatasetNode dataset) {

            target =
                    dataset.dataset
                            .getDsname();

        } else {

            return;
        }

        setState(
                "Opening "
                        + target
                        + "...");

        runBackground(project, () -> {

            String content =
                    new DataSetService(
                            ZoweConnectionProvider.current())
                            .read(target);

            SwingUtilities.invokeLater(() -> {

                remoteEditors.openDataSet(
                        target,
                        content);

                setState(
                        "Opened "
                                + target
                                + " in an IntelliJ editor tab. "
                                + "Ctrl+S / Save All writes changes to z/OS.");
            });
        });
    }

    private void showSelectedDsnNode() {

        DefaultMutableTreeNode node =
                selectedNode(dsnTree);

        if (node == null) {
            return;
        }

        Object value =
                node.getUserObject();

        if (value
                instanceof DatasetNode dataset) {

            dsnDetails.setText(
                    formatDataset(
                            dataset.dataset));

        } else if (value
                instanceof MemberNode member) {

            dsnDetails.setText(
                    member.member.toString());
        }

        dsnDetails.setCaretPosition(0);
    }

    // -------------------------------------------------------------------------
    // USS
    // -------------------------------------------------------------------------

    private JComponent createUssPanel(
            Project project) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                4,
                                4));

        JPanel toolbar =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JButton list =
                new JButton("List");

        JButton open =
                new JButton("Open");

        JButton up =
                new JButton("Up");

        ussPath.setColumns(30);

        list.addActionListener(
                e -> listUss(
                        project,
                        ussPath.getText()));

        open.addActionListener(
                e -> openUssSelection(project));

        up.addActionListener(e -> {

            String parent =
                    parentPath(
                            ussPath.getText());

            ussPath.setText(parent);

            listUss(
                    project,
                    parent);
        });

        ussPath.addActionListener(
                e -> listUss(
                        project,
                        ussPath.getText()));

        toolbar.add(
                new JBLabel("Path:"));

        toolbar.add(ussPath);
        toolbar.add(list);
        toolbar.add(up);
        toolbar.add(open);

        ussTree.setRootVisible(true);

        ussTree.addTreeSelectionListener(
                e -> showSelectedUssNode());

        ussTree.addMouseListener(
                new MouseAdapter() {

                    @Override
                    public void mouseClicked(
                            MouseEvent e) {

                        if (e.getClickCount() == 2) {

                            openUssSelection(
                                    project);
                        }
                    }
                });

        JBSplitter splitter =
                new JBSplitter(
                        false,
                        0.35f);

        splitter.setFirstComponent(
                new JBScrollPane(
                        ussTree));

        splitter.setSecondComponent(
                new JBScrollPane(
                        ussDetails));

        panel.add(
                toolbar,
                BorderLayout.NORTH);

        panel.add(
                splitter,
                BorderLayout.CENTER);

        return panel;
    }

    private void listUss(
            Project project,
            String requestedPath) {

        String path =
                normalizePath(
                        requestedPath);

        ussPath.setText(path);

        setState(
                "Listing USS "
                        + path
                        + "...");

        runBackground(project, () -> {

            List<UnixFile> items =
                    new UssService(
                            ZoweConnectionProvider.current())
                            .list(path);

            SwingUtilities.invokeLater(() -> {

                ussRoot.removeAllChildren();

                for (UnixFile item : items) {

                    ussRoot.add(
                            new DefaultMutableTreeNode(
                                    new UssNode(
                                            path,
                                            item)));
                }

                ussModel.reload();

                ussTree.expandRow(0);

                setState(
                        "Loaded "
                                + items.size()
                                + " USS entries from "
                                + path
                                + ".");
            });
        });
    }

    private void openUssSelection(
            Project project) {

        DefaultMutableTreeNode node =
                selectedNode(ussTree);

        if (node == null
                || !(node.getUserObject()
                instanceof UssNode item)) {

            return;
        }

        String fullPath =
                item.fullPath();

        if (item.isDirectory()) {

            ussPath.setText(
                    fullPath);

            listUss(
                    project,
                    fullPath);

            return;
        }

        setState(
                "Opening USS file "
                        + fullPath
                        + "...");

        runBackground(project, () -> {

            String content =
                    new UssService(
                            ZoweConnectionProvider.current())
                            .readText(fullPath);

            SwingUtilities.invokeLater(() -> {

                remoteEditors.openUss(
                        fullPath,
                        content);

                setState(
                        "Opened "
                                + fullPath
                                + " in an IntelliJ editor tab. "
                                + "Ctrl+S / Save All writes changes to z/OS.");
            });
        });
    }

    private void showSelectedUssNode() {

        DefaultMutableTreeNode node =
                selectedNode(ussTree);

        if (node != null
                && node.getUserObject()
                instanceof UssNode item) {

            ussDetails.setText(
                    item.file.toString());

            ussDetails.setCaretPosition(0);
        }
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    private JComponent createCommandsPanel(
            Project project) {

        JTabbedPane commandTabs =
                new JTabbedPane();

        commandTabs.addTab(
                "TSO",
                createTsoCommandPanel(project));

        commandTabs.addTab(
                "MVS Console",
                createConsoleCommandPanel(project));

        commandTabs.addTab(
                "USS SSH",
                createSshCommandPanel(project));

        return commandTabs;
    }

    private JComponent createTsoCommandPanel(
            Project project) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                4,
                                4));

        JPanel top =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JButton start =
                new JButton("Start Session");

        JButton stop =
                new JButton("Stop Session");

        JButton clear =
                new JButton("Clear");

        start.addActionListener(
                e -> startTsoSession(project));

        stop.addActionListener(
                e -> stopTsoSession(project));

        clear.addActionListener(
                e -> tsoOutput.setText(""));

        top.add(start);
        top.add(stop);
        top.add(clear);
        top.add(tsoSessionState);

        JPanel entry =
                new JPanel(
                        new BorderLayout(
                                4,
                                2));

        JButton run =
                new JButton("Run");

        tsoInput.setToolTipText(
                "Issue a TSO command using the active reusable TSO session");

        tsoInput.addActionListener(
                e -> issueTso(project));

        run.addActionListener(
                e -> issueTso(project));

        entry.add(
                new JBLabel("TSO> "),
                BorderLayout.WEST);

        entry.add(
                tsoInput,
                BorderLayout.CENTER);

        entry.add(
                run,
                BorderLayout.EAST);

        panel.add(
                top,
                BorderLayout.NORTH);

        panel.add(
                new JBScrollPane(
                        tsoOutput),
                BorderLayout.CENTER);

        panel.add(
                entry,
                BorderLayout.SOUTH);

        return panel;
    }

    private JComponent createConsoleCommandPanel(
            Project project) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                4,
                                4));

        JPanel top =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JButton clear =
                new JButton("Clear");

        clear.addActionListener(
                e -> consoleOutput.setText(""));

        top.add(
                new JBLabel(
                        "Synchronous z/OSMF console commands"));

        top.add(clear);

        JPanel entry =
                new JPanel(
                        new BorderLayout(
                                4,
                                2));

        JButton run =
                new JButton("Run");

        consoleInput.addActionListener(
                e -> issueConsole(project));

        run.addActionListener(
                e -> issueConsole(project));

        entry.add(
                new JBLabel("MVS> "),
                BorderLayout.WEST);

        entry.add(
                consoleInput,
                BorderLayout.CENTER);

        entry.add(
                run,
                BorderLayout.EAST);

        panel.add(
                top,
                BorderLayout.NORTH);

        panel.add(
                new JBScrollPane(
                        consoleOutput),
                BorderLayout.CENTER);

        panel.add(
                entry,
                BorderLayout.SOUTH);

        return panel;
    }

    private JComponent createSshCommandPanel(
            Project project) {

        JPanel panel =
                new JPanel(
                        new BorderLayout(
                                4,
                                4));

        JPanel top =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.LEFT,
                                4,
                                2));

        JButton clear =
                new JButton("Clear");

        clear.addActionListener(
                e -> sshOutput.setText(""));

        top.add(
                new JBLabel(
                        "USS command execution over SSH "
                                + "(known_hosts verification enabled)"));

        top.add(clear);

        JPanel entry =
                new JPanel(
                        new BorderLayout(
                                4,
                                2));

        JButton run =
                new JButton("Run");

        sshInput.addActionListener(
                e -> issueSsh(project));

        run.addActionListener(
                e -> issueSsh(project));

        entry.add(
                new JBLabel("USS$ "),
                BorderLayout.WEST);

        entry.add(
                sshInput,
                BorderLayout.CENTER);

        entry.add(
                run,
                BorderLayout.EAST);

        panel.add(
                top,
                BorderLayout.NORTH);

        panel.add(
                new JBScrollPane(
                        sshOutput),
                BorderLayout.CENTER);

        panel.add(
                entry,
                BorderLayout.SOUTH);

        return panel;
    }

    private CommandService commandService() {

        CommandService service =
                commandService;

        if (service == null) {

            synchronized (this) {

                service =
                        commandService;

                if (service == null) {

                    commandService =
                            service =
                                    ZoweConnectionProvider
                                            .currentCommandService();
                }
            }
        }

        return service;
    }

    private void startTsoSession(
            Project project) {

        setState(
                "Starting reusable TSO session...");

        runBackground(project, () -> {

            String session =
                    commandService()
                            .startTsoSession();

            SwingUtilities.invokeLater(() -> {

                tsoSessionState.setText(
                        "TSO session: "
                                + session);

                appendTerminal(
                        tsoOutput,
                        "[session started: "
                                + session
                                + "]\n");

                setState(
                        "TSO session started: "
                                + session);
            });
        });
    }

    private void stopTsoSession(
            Project project) {

        setState(
                "Stopping TSO session...");

        runBackground(project, () -> {

            commandService()
                    .stopTsoSession();

            SwingUtilities.invokeLater(() -> {

                tsoSessionState.setText(
                        "TSO session: stopped");

                appendTerminal(
                        tsoOutput,
                        "[session stopped]\n");

                setState(
                        "TSO session stopped.");
            });
        });
    }

    private void issueTso(
            Project project) {

        String command =
                tsoInput.getText().trim();

        if (command.isEmpty()) {
            return;
        }

        tsoInput.setText("");

        appendTerminal(
                tsoOutput,
                "TSO> "
                        + command
                        + "\n");

        setState(
                "Running TSO command...");

        runBackground(project, () -> {

            List<String> output =
                    commandService()
                            .issueTso(command);

            String session =
                    commandService()
                            .getTsoSessionId();

            SwingUtilities.invokeLater(() -> {

                tsoSessionState.setText(
                        "TSO session: "
                                + session);

                if (output.isEmpty()) {

                    appendTerminal(
                            tsoOutput,
                            "(no output)\n");

                } else {

                    output.forEach(
                            line ->
                                    appendTerminal(
                                            tsoOutput,
                                            line + "\n"));
                }

                appendTerminal(
                        tsoOutput,
                        "\n");

                setState(
                        "TSO command completed using session "
                                + session
                                + ".");
            });
        });
    }

    private void issueConsole(
            Project project) {

        String command =
                consoleInput
                        .getText()
                        .trim();

        if (command.isEmpty()) {
            return;
        }

        consoleInput.setText("");

        appendTerminal(
                consoleOutput,
                "MVS> "
                        + command
                        + "\n");

        setState(
                "Running MVS console command...");

        runBackground(project, () -> {

            String output =
                    commandService()
                            .issueConsole(command);

            SwingUtilities.invokeLater(() -> {

                appendTerminal(
                        consoleOutput,
                        output + "\n\n");

                setState(
                        "MVS console command completed.");
            });
        });
    }

    private void issueSsh(
            Project project) {

        String command =
                sshInput
                        .getText()
                        .trim();

        if (command.isEmpty()) {
            return;
        }

        sshInput.setText("");

        appendTerminal(
                sshOutput,
                "USS$ "
                        + command
                        + "\n");

        setState(
                "Running USS SSH command...");

        runBackground(project, () -> {

            String output =
                    commandService()
                            .issueUss(command);

            SwingUtilities.invokeLater(() -> {

                appendTerminal(
                        sshOutput,
                        output
                                + (output.endsWith("\n")
                                ? "\n"
                                : "\n\n"));

                setState(
                        "USS SSH command completed.");
            });
        });
    }

    private static void appendTerminal(
            JBTextArea area,
            String text) {

        area.append(text);

        area.setCaretPosition(
                area.getDocument()
                        .getLength());
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private Job selectedJob(
            Project project) {

        DefaultMutableTreeNode node =
                selectedNode(jobsTree);

        if (node != null) {

            Object value =
                    node.getUserObject();

            if (value
                    instanceof JobNode j) {

                return j.job;
            }

            if (value
                    instanceof SpoolNode s) {

                return s.job;
            }
        }

        Messages.showInfoMessage(
                project,
                "Select a job first.",
                "Zowe Java Explorer");

        return null;
    }

    private DefaultMutableTreeNode selectedNode(
            JTree tree) {

        TreePath path =
                tree.getSelectionPath();

        return path == null
                ? null
                : (DefaultMutableTreeNode)
                path.getLastPathComponent();
    }

    private DefaultMutableTreeNode findJobNode(
            Job job) {

        for (int i = 0;
             i < jobsRoot.getChildCount();
             i++) {

            DefaultMutableTreeNode node =
                    (DefaultMutableTreeNode)
                            jobsRoot.getChildAt(i);

            Object value =
                    node.getUserObject();

            if (value
                    instanceof JobNode j
                    && j.job.getJobName()
                    .equals(job.getJobName())
                    && j.job.getJobId()
                    .equals(job.getJobId())) {

                return node;
            }
        }

        return null;
    }

    private void runBackground(
            Project project,
            ThrowingRunnable task) {

        ApplicationManager
                .getApplication()
                .executeOnPooledThread(() -> {

                    try {

                        task.run();

                    } catch (Exception ex) {

                        showError(
                                project,
                                ex);
                    }
                });
    }

    private void showError(
            Project project,
            Exception ex) {

        SwingUtilities.invokeLater(() -> {

            setState(
                    "Error: "
                            + ex.getMessage());

            Messages.showErrorDialog(
                    project,
                    ex.toString(),
                    "Zowe Java Explorer");
        });
    }

    private void setState(
            String text) {

        globalState.setText(text);
    }

    private static JBTextArea textArea(
            boolean editable) {

        JBTextArea area =
                new JBTextArea();

        area.setEditable(editable);

        area.setLineWrap(false);

        area.setFont(
                new Font(
                        Font.MONOSPACED,
                        Font.PLAIN,
                        area.getFont()
                                .getSize()));

        return area;
    }

    private String formatJob(
            Job job) {

        return "Job Name : "
                + job.getJobName()
                + "\n"
                + "Job ID   : "
                + job.getJobId()
                + "\n"
                + "Owner    : "
                + job.getOwner()
                + "\n"
                + "Status   : "
                + job.getStatus()
                + "\n"
                + "Retcode  : "
                + job.getRetCode()
                + "\n";
    }

    private String formatDataset(
            Dataset d) {

        return "Data Set : "
                + d.getDsname()
                + "\n"
                + "DSORG    : "
                + d.getDsorg()
                + "\n"
                + "RECFM    : "
                + d.getRecfm()
                + "\n"
                + "LRECL    : "
                + d.getLrectl()
                + "\n"
                + "BLKSIZE  : "
                + d.getBlksz()
                + "\n"
                + "Volume   : "
                + d.getVol()
                + "\n"
                + "Created  : "
                + d.getCdate()
                + "\n"
                + "Used     : "
                + d.getUsed()
                + "\n";
    }

    private static String normalizePath(
            String path) {

        if (path == null
                || path.isBlank()) {

            return "/";
        }

        String p =
                path.trim();

        if (!p.startsWith("/")) {

            p =
                    "/" + p;
        }

        while (p.length() > 1
                && p.endsWith("/")) {

            p =
                    p.substring(
                            0,
                            p.length() - 1);
        }

        return p;
    }

    private static String parentPath(
            String path) {

        String p =
                normalizePath(path);

        if ("/".equals(p)) {
            return "/";
        }

        int slash =
                p.lastIndexOf('/');

        return slash <= 0
                ? "/"
                : p.substring(
                0,
                slash);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    // -------------------------------------------------------------------------
    // Tree node models
    // -------------------------------------------------------------------------

    private static final class JobNode {

        private final Job job;

        private JobNode(
                Job job) {

            this.job =
                    job;
        }

        @Override
        public String toString() {

            return job.getJobName()
                    + " / "
                    + job.getJobId()
                    + "  ["
                    + job.getStatus()
                    + "]";
        }
    }

    private static final class SpoolNode {

        private final Job job;
        private final JobFile file;

        private SpoolNode(
                Job job,
                JobFile file) {

            this.job =
                    job;

            this.file =
                    file;
        }

        @Override
        public String toString() {

            return file.toString();
        }
    }

    private static final class DatasetNode {

        private final Dataset dataset;

        private DatasetNode(
                Dataset dataset) {

            this.dataset =
                    dataset;
        }

        @Override
        public String toString() {

            return dataset.getDsname();
        }
    }

    private static final class MemberNode {

        private final String dataSetName;
        private final Member member;

        private MemberNode(
                String dataSetName,
                Member member) {

            this.dataSetName =
                    dataSetName;

            this.member =
                    member;
        }

        private String qualifiedName() {

            return dataSetName
                    + "("
                    + member.getMember()
                    + ")";
        }

        @Override
        public String toString() {

            return member.getMember();
        }
    }

    private static final class UssNode {

        private final String parent;
        private final UnixFile file;

        private UssNode(
                String parent,
                UnixFile file) {

            this.parent =
                    parent;

            this.file =
                    file;
        }

        private boolean isDirectory() {

            return file.getMode() != null
                    && file.getMode()
                    .startsWith("d");
        }

        private String fullPath() {

            if (file.getName()
                    .startsWith("/")) {

                return file.getName();
            }

            return "/".equals(parent)
                    ? "/"
                    + file.getName()
                    : parent
                    + "/"
                    + file.getName();
        }

        @Override
        public String toString() {

            String suffix =
                    isDirectory()
                            ? "/"
                            : "";

            return file.getName()
                    + suffix
                    + "  ["
                    + file.getMode()
                    + ", "
                    + file.getSize()
                    + " bytes]";
        }
    }
}