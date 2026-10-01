package org.zowe.explorerjava;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class ConnectionDialog extends DialogWrapper {
    private final JBTextField host = new JBTextField();
    private final JBTextField port = new JBTextField();
    private final JBTextField user = new JBTextField();
    private final JBPasswordField password = new JBPasswordField();
    private final JBTextField tsoAccount = new JBTextField();
    private final JBTextField sshPort = new JBTextField();
    private final JBTextField sshTimeout = new JBTextField();

    public ConnectionDialog() {
        super(true);
        setTitle("Zowe Connection");
        ZoweConnectionSettings s = ZoweConnectionSettings.getInstance();
        host.setText(s.getHost());
        port.setText(String.valueOf(s.getPort()));
        user.setText(s.getUser());
        password.setText(s.getPassword());
        tsoAccount.setText(s.getTsoAccount());
        sshPort.setText(String.valueOf(s.getSshPort()));
        sshTimeout.setText(String.valueOf(s.getSshTimeoutMillis()));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel p = new JPanel(new GridLayout(7, 2, 8, 8));
        p.add(new JLabel("z/OS host"));
        p.add(host);
        p.add(new JLabel("z/OSMF port"));
        p.add(port);
        p.add(new JLabel("User"));
        p.add(user);
        p.add(new JLabel("Password"));
        p.add(password);
        p.add(new JLabel("TSO account"));
        p.add(tsoAccount);
        p.add(new JLabel("SSH port"));
        p.add(sshPort);
        p.add(new JLabel("SSH timeout (ms)"));
        p.add(sshTimeout);
        return p;
    }

    @Override
    protected void doOKAction() {
        int zosmfPort;
        int sshPortValue;
        int sshTimeoutValue;
        try {
            zosmfPort = Integer.parseInt(port.getText().trim());
            sshPortValue = Integer.parseInt(sshPort.getText().trim());
            sshTimeoutValue = Integer.parseInt(sshTimeout.getText().trim());
        } catch (NumberFormatException e) {
            setErrorText("Ports and SSH timeout must be numeric.");
            return;
        }
        if (zosmfPort < 1 || zosmfPort > 65535 || sshPortValue < 1 || sshPortValue > 65535) {
            setErrorText("Ports must be between 1 and 65535.");
            return;
        }
        if (sshTimeoutValue <= 0) {
            setErrorText("SSH timeout must be greater than zero.");
            return;
        }
        if (host.getText().isBlank() || user.getText().isBlank()) {
            setErrorText("Host and user are required.");
            return;
        }
        ZoweConnectionSettings.getInstance().save(
                host.getText(), zosmfPort, user.getText(), new String(password.getPassword()),
                sshPortValue, sshTimeoutValue, tsoAccount.getText());
        super.doOKAction();
    }
}
