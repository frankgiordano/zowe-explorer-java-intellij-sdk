package org.zowe.explorerjava;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(name = "ZoweJavaExplorerSettings", storages = @Storage("zoweJavaExplorer.xml"))
public final class ZoweConnectionSettings implements PersistentStateComponent<ZoweConnectionSettings.StateData> {
    private static final String CREDENTIAL_KEY = "Zowe Java Explorer z/OSMF";

    public static final class StateData {
        public String host = "";
        public int port = 443;
        public String user = "";
        public int sshPort = 22;
        public int sshTimeoutMillis = 30000;
        public String tsoAccount = "";
        public List<String> dsnMaskHistory = new ArrayList<>();
    }

    private StateData state = new StateData();

    public static ZoweConnectionSettings getInstance() {
        return ApplicationManager.getApplication().getService(ZoweConnectionSettings.class);
    }

    @Override
    public StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public String getHost() {
        return state.host;
    }

    public int getPort() {
        return state.port;
    }

    public String getUser() {
        return state.user;
    }

    public int getSshPort() {
        return state.sshPort;
    }

    public int getSshTimeoutMillis() {
        return state.sshTimeoutMillis;
    }

    public String getTsoAccount() {
        return state.tsoAccount;
    }

    public List<String> getDsnMaskHistory() {
        if (state.dsnMaskHistory == null) {
            state.dsnMaskHistory = new ArrayList<>();
        }
        return new ArrayList<>(state.dsnMaskHistory);
    }

    public void addDsnMaskToHistory(String mask) {
        if (mask == null || mask.isBlank()) {
            return;
        }
        String trimmed = mask.trim();
        if (state.dsnMaskHistory == null) {
            state.dsnMaskHistory = new ArrayList<>();
        }
        state.dsnMaskHistory.remove(trimmed);
        state.dsnMaskHistory.add(0, trimmed);
        while (state.dsnMaskHistory.size() > 20) {
            state.dsnMaskHistory.remove(state.dsnMaskHistory.size() - 1);
        }
    }

    public void removeDsnMaskFromHistory(String mask) {
        if (mask == null || mask.isBlank()) {
            return;
        }
        if (state.dsnMaskHistory != null) {
            state.dsnMaskHistory.remove(mask.trim());
        }
    }

    public void clearDsnMaskHistory() {
        if (state.dsnMaskHistory != null) {
            state.dsnMaskHistory.clear();
        }
    }

    public String getPassword() {
        Credentials c = PasswordSafe.getInstance().get(new CredentialAttributes(CREDENTIAL_KEY, state.user));
        return c == null || c.getPasswordAsString() == null ? "" : c.getPasswordAsString();
    }

    public void save(String host, int port, String user, String password,
                     int sshPort, int sshTimeoutMillis, String tsoAccount) {
        state.host = host.trim();
        state.port = port;
        state.user = user.trim();
        state.sshPort = sshPort;
        state.sshTimeoutMillis = sshTimeoutMillis;
        state.tsoAccount = tsoAccount == null ? "" : tsoAccount.trim();
        PasswordSafe.getInstance().set(
                new CredentialAttributes(CREDENTIAL_KEY, state.user),
                new Credentials(state.user, password));
    }
}
