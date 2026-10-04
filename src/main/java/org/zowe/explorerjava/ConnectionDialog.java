package org.zowe.explorerjava;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;
import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.core.ZosConnectionFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class ConnectionDialog extends DialogWrapper {
    private final DefaultListModel<ConnectionProfile> listModel = new DefaultListModel<>();
    private final JBList<ConnectionProfile> profileList = new JBList<>(listModel);

    private final JBTextField profileName = new JBTextField();
    private final JBTextField host = new JBTextField();
    private final JBTextField port = new JBTextField();
    private final JBTextField user = new JBTextField();
    private final JBPasswordField password = new JBPasswordField();
    private final JBTextField tsoAccount = new JBTextField();
    private final JBTextField sshPort = new JBTextField();
    private final JBTextField sshTimeout = new JBTextField();
    private final JBCheckBox activeCheckBox = new JBCheckBox("Set as active connection");

    private final JButton testButton = new JButton("Test Connection", AllIcons.Actions.Execute);

    private final Map<String, String> workingPasswords = new HashMap<>();
    private String activeProfileId;
    private ConnectionProfile currentEditingProfile;
    private boolean isUpdatingForm = false;

    public ConnectionDialog() {
        super(true);
        setTitle("Zowe Connections Manager");

        ZoweConnectionSettings settings = ZoweConnectionSettings.getInstance();
        List<ConnectionProfile> existingProfiles = settings.getProfiles();
        activeProfileId = settings.getActiveProfile().id;

        if (existingProfiles.isEmpty()) {
            ConnectionProfile defaultProfile = new ConnectionProfile(
                    UUID.randomUUID().toString(),
                    "Mainframe z/OS",
                    "",
                    443,
                    "",
                    22,
                    30000,
                    ""
            );
            existingProfiles.add(defaultProfile);
            activeProfileId = defaultProfile.id;
        }

        for (ConnectionProfile p : existingProfiles) {
            ConnectionProfile copy = p.copy();
            listModel.addElement(copy);
            workingPasswords.put(copy.id, settings.getPasswordForProfile(copy.id));
        }

        init();

        profileList.addListSelectionListener(this::onProfileSelectionChanged);
        selectProfileById(activeProfileId);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        profileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        profileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ConnectionProfile p) {
                    String prefix = p.id.equals(activeProfileId) ? "★ " : "   ";
                    setText(prefix + p.getDisplayName());
                }
                return c;
            }
        });

        leftPanel.add(new JBScrollPane(profileList), BorderLayout.CENTER);

        JPanel listToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton addButton = new JButton(AllIcons.General.Add);
        addButton.setToolTipText("Add new connection profile");
        addButton.addActionListener(e -> addNewProfile());

        JButton duplicateButton = new JButton(AllIcons.Actions.Copy);
        duplicateButton.setToolTipText("Duplicate selected connection profile");
        duplicateButton.addActionListener(e -> duplicateSelectedProfile());

        JButton removeButton = new JButton(AllIcons.General.Remove);
        removeButton.setToolTipText("Remove selected connection profile");
        removeButton.addActionListener(e -> removeSelectedProfile());

        listToolbar.add(addButton);
        listToolbar.add(duplicateButton);
        listToolbar.add(removeButton);
        leftPanel.add(listToolbar, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        JPanel formPanel = new JPanel(new GridLayout(9, 2, 8, 8));

        formPanel.add(new JLabel("Connection Name:"));
        formPanel.add(profileName);

        formPanel.add(new JLabel("z/OS Host:"));
        formPanel.add(host);

        formPanel.add(new JLabel("z/OSMF Port:"));
        formPanel.add(port);

        formPanel.add(new JLabel("User:"));
        formPanel.add(user);

        formPanel.add(new JLabel("Password:"));
        formPanel.add(password);

        formPanel.add(new JLabel("TSO Account:"));
        formPanel.add(tsoAccount);

        formPanel.add(new JLabel("SSH Port:"));
        formPanel.add(sshPort);

        formPanel.add(new JLabel("SSH Timeout (ms):"));
        formPanel.add(sshTimeout);

        formPanel.add(new JLabel("Active:"));
        formPanel.add(activeCheckBox);

        activeCheckBox.addActionListener(e -> {
            if (currentEditingProfile != null && activeCheckBox.isSelected()) {
                activeProfileId = currentEditingProfile.id;
                profileList.repaint();
            }
        });

        rightPanel.add(formPanel, BorderLayout.CENTER);

        JPanel testToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        testButton.addActionListener(e -> testCurrentConnection());
        testToolbar.add(testButton);
        rightPanel.add(testToolbar, BorderLayout.SOUTH);

        JBSplitter splitter = new JBSplitter(false, 0.35f);
        splitter.setFirstComponent(leftPanel);
        splitter.setSecondComponent(rightPanel);
        splitter.setPreferredSize(new Dimension(640, 360));

        return splitter;
    }

    private void onProfileSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() || isUpdatingForm) {
            return;
        }
        saveCurrentFormToProfile();

        ConnectionProfile selected = profileList.getSelectedValue();
        if (selected != null) {
            loadProfileToForm(selected);
        }
    }

    private void saveCurrentFormToProfile() {
        if (currentEditingProfile == null) {
            return;
        }
        currentEditingProfile.name = profileName.getText().trim();
        currentEditingProfile.host = host.getText().trim();
        try {
            currentEditingProfile.port = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        currentEditingProfile.user = user.getText().trim();
        workingPasswords.put(currentEditingProfile.id, new String(password.getPassword()));
        currentEditingProfile.tsoAccount = tsoAccount.getText().trim();
        try {
            currentEditingProfile.sshPort = Integer.parseInt(sshPort.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        try {
            currentEditingProfile.sshTimeoutMillis = Integer.parseInt(sshTimeout.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        profileList.repaint();
    }

    private void loadProfileToForm(ConnectionProfile p) {
        isUpdatingForm = true;
        try {
            currentEditingProfile = p;
            profileName.setText(p.name);
            host.setText(p.host);
            port.setText(String.valueOf(p.port));
            user.setText(p.user);
            password.setText(workingPasswords.getOrDefault(p.id, ""));
            tsoAccount.setText(p.tsoAccount);
            sshPort.setText(String.valueOf(p.sshPort));
            sshTimeout.setText(String.valueOf(p.sshTimeoutMillis));
            activeCheckBox.setSelected(p.id.equals(activeProfileId));
        } finally {
            isUpdatingForm = false;
        }
    }

    private void selectProfileById(String id) {
        for (int i = 0; i < listModel.getSize(); i++) {
            if (listModel.getElementAt(i).id.equals(id)) {
                profileList.setSelectedIndex(i);
                return;
            }
        }
        if (listModel.getSize() > 0) {
            profileList.setSelectedIndex(0);
        }
    }

    private void addNewProfile() {
        saveCurrentFormToProfile();
        int count = listModel.getSize() + 1;
        ConnectionProfile newProfile = new ConnectionProfile(
                UUID.randomUUID().toString(),
                "Connection " + count,
                "",
                443,
                "",
                22,
                30000,
                ""
        );
        listModel.addElement(newProfile);
        workingPasswords.put(newProfile.id, "");
        selectProfileById(newProfile.id);
    }

    private void duplicateSelectedProfile() {
        saveCurrentFormToProfile();
        ConnectionProfile selected = profileList.getSelectedValue();
        if (selected == null) {
            return;
        }
        ConnectionProfile copy = selected.copy();
        copy.id = UUID.randomUUID().toString();
        copy.name = selected.getDisplayName() + " (Copy)";
        listModel.addElement(copy);
        workingPasswords.put(copy.id, workingPasswords.getOrDefault(selected.id, ""));
        selectProfileById(copy.id);
    }

    private void removeSelectedProfile() {
        if (listModel.getSize() <= 1) {
            Messages.showWarningDialog(getContentPanel(), "At least one connection profile must remain.", "Cannot Remove");
            return;
        }
        ConnectionProfile selected = profileList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int index = profileList.getSelectedIndex();
        listModel.removeElement(selected);
        workingPasswords.remove(selected.id);

        if (selected.id.equals(activeProfileId)) {
            ConnectionProfile nextActive = listModel.getElementAt(Math.max(0, index - 1));
            activeProfileId = nextActive.id;
        }

        selectProfileById(activeProfileId);
    }

    private void testCurrentConnection() {
        saveCurrentFormToProfile();
        if (currentEditingProfile == null) {
            return;
        }

        String hostText = host.getText().trim();
        String userText = user.getText().trim();
        String passText = new String(password.getPassword());
        int zosmfPort;
        try {
            zosmfPort = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException ex) {
            Messages.showErrorDialog(getContentPanel(), "z/OSMF Port must be numeric.", "Connection Test Failed");
            return;
        }

        if (hostText.isBlank() || userText.isBlank()) {
            Messages.showErrorDialog(getContentPanel(), "z/OS Host and User are required to test the connection.", "Connection Test Failed");
            return;
        }

        testButton.setEnabled(false);
        testButton.setText("Testing...");

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                ZosConnection testConn = ZosConnectionFactory.createBasicConnection(
                        hostText, zosmfPort, userText, passText);
                DataSetService testService = new DataSetService(testConn);
                testService.list(userText + ".*");

                SwingUtilities.invokeLater(() -> {
                    testButton.setEnabled(true);
                    testButton.setText("Test Connection");
                    Messages.showInfoMessage(
                            getContentPanel(),
                            "Successfully connected to z/OSMF at " + hostText + ":" + zosmfPort + " as " + userText + ".",
                            "Connection Test Succeeded"
                    );
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    testButton.setEnabled(true);
                    testButton.setText("Test Connection");
                    Messages.showErrorDialog(
                            getContentPanel(),
                            "Failed to connect to " + hostText + ":" + zosmfPort + " as " + userText + ".\n\n" + ex.getMessage(),
                            "Connection Test Failed"
                    );
                });
            }
        });
    }

    @Override
    protected void doOKAction() {
        saveCurrentFormToProfile();

        List<ConnectionProfile> resultProfiles = new ArrayList<>();
        Set<String> validIds = new HashSet<>();

        for (int i = 0; i < listModel.getSize(); i++) {
            ConnectionProfile p = listModel.getElementAt(i);
            if (p.name.isBlank()) {
                p.name = p.getDisplayName();
            }
            if (p.host.isBlank() || p.user.isBlank()) {
                setErrorText("All connection profiles require a Host and User (" + p.getDisplayName() + ").");
                selectProfileById(p.id);
                return;
            }
            if (p.port < 1 || p.port > 65535 || p.sshPort < 1 || p.sshPort > 65535) {
                setErrorText("Ports must be between 1 and 65535 (" + p.getDisplayName() + ").");
                selectProfileById(p.id);
                return;
            }
            if (p.sshTimeoutMillis <= 0) {
                setErrorText("SSH timeout must be greater than zero (" + p.getDisplayName() + ").");
                selectProfileById(p.id);
                return;
            }
            resultProfiles.add(p);
            validIds.add(p.id);
        }

        ZoweConnectionSettings settings = ZoweConnectionSettings.getInstance();

        // Clean up passwords for removed profiles
        List<ConnectionProfile> oldProfiles = settings.getProfiles();
        for (ConnectionProfile old : oldProfiles) {
            if (!validIds.contains(old.id)) {
                settings.removePasswordForProfile(old.id);
            }
        }

        // Save new profiles and their passwords
        settings.setProfiles(resultProfiles, activeProfileId);
        for (ConnectionProfile p : resultProfiles) {
            String pass = workingPasswords.getOrDefault(p.id, "");
            settings.setPasswordForProfile(p.id, pass);
        }

        super.doOKAction();
    }
}
