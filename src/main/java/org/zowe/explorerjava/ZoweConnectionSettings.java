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
import java.util.UUID;

@State(name = "ZoweJavaExplorerSettings", storages = @Storage("zoweJavaExplorer.xml"))
public final class ZoweConnectionSettings implements PersistentStateComponent<ZoweConnectionSettings.StateData> {
    private static final String CREDENTIAL_PREFIX = "Zowe Java Explorer z/OSMF: ";
    private static final String LEGACY_CREDENTIAL_KEY = "Zowe Java Explorer z/OSMF";

    public static final class StateData {
        // Legacy single-connection fields for backward compatibility
        public String host = "";
        public int port = 443;
        public String user = "";
        public int sshPort = 22;
        public int sshTimeoutMillis = 30000;
        public String tsoAccount = "";

        public List<String> dsnMaskHistory = new ArrayList<>();

        // Multi-connection fields
        public List<ConnectionProfile> profiles = new ArrayList<>();
        public String activeProfileId = "";
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
        ensureMigrated();
    }

    private synchronized void ensureMigrated() {
        if (state.profiles == null) {
            state.profiles = new ArrayList<>();
        }
        // Migrate legacy single-connection configuration if present and profiles list is empty
        if (state.profiles.isEmpty() && state.host != null && !state.host.isBlank()) {
            String profileId = UUID.randomUUID().toString();
            String profileName = state.user.isBlank() ? state.host : state.user + "@" + state.host;
            ConnectionProfile profile = new ConnectionProfile(
                    profileId,
                    profileName,
                    state.host,
                    state.port > 0 ? state.port : 443,
                    state.user,
                    state.sshPort > 0 ? state.sshPort : 22,
                    state.sshTimeoutMillis > 0 ? state.sshTimeoutMillis : 30000,
                    state.tsoAccount
            );
            state.profiles.add(profile);
            state.activeProfileId = profileId;

            // Migrate legacy password from PasswordSafe
            Credentials legacyCredentials = PasswordSafe.getInstance().get(new CredentialAttributes(LEGACY_CREDENTIAL_KEY, state.user));
            if (legacyCredentials != null && legacyCredentials.getPasswordAsString() != null) {
                setPasswordForProfile(profileId, legacyCredentials.getPasswordAsString());
            }
        }
    }

    public List<ConnectionProfile> getProfiles() {
        ensureMigrated();
        return new ArrayList<>(state.profiles);
    }

    public ConnectionProfile getActiveProfile() {
        ensureMigrated();
        if (state.profiles.isEmpty()) {
            return new ConnectionProfile();
        }
        for (ConnectionProfile p : state.profiles) {
            if (p.id.equals(state.activeProfileId)) {
                return p;
            }
        }
        // Fallback to first profile if activeProfileId not found
        state.activeProfileId = state.profiles.get(0).id;
        return state.profiles.get(0);
    }

    public void setActiveProfileId(String profileId) {
        ensureMigrated();
        if (profileId != null) {
            for (ConnectionProfile p : state.profiles) {
                if (p.id.equals(profileId)) {
                    state.activeProfileId = profileId;
                    break;
                }
            }
        }
    }

    public void setProfiles(List<ConnectionProfile> newProfiles, String activeProfileId) {
        ensureMigrated();
        state.profiles = new ArrayList<>(newProfiles);
        if (activeProfileId != null) {
            state.activeProfileId = activeProfileId;
        }
        if (state.profiles.isEmpty()) {
            state.activeProfileId = "";
        } else {
            boolean activeExists = false;
            for (ConnectionProfile p : state.profiles) {
                if (p.id.equals(state.activeProfileId)) {
                    activeExists = true;
                    break;
                }
            }
            if (!activeExists) {
                state.activeProfileId = state.profiles.get(0).id;
            }
        }
    }

    public String getPasswordForProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return "";
        }
        Credentials c = PasswordSafe.getInstance().get(new CredentialAttributes(CREDENTIAL_PREFIX + profileId, profileId));
        return c == null || c.getPasswordAsString() == null ? "" : c.getPasswordAsString();
    }

    public void setPasswordForProfile(String profileId, String password) {
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        PasswordSafe.getInstance().set(
                new CredentialAttributes(CREDENTIAL_PREFIX + profileId, profileId),
                new Credentials(profileId, password));
    }

    public void removePasswordForProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return;
        }
        PasswordSafe.getInstance().set(
                new CredentialAttributes(CREDENTIAL_PREFIX + profileId, profileId),
                null);
    }

    // Direct active profile convenience getters for existing callers
    public String getHost() {
        return getActiveProfile().host;
    }

    public int getPort() {
        return getActiveProfile().port;
    }

    public String getUser() {
        return getActiveProfile().user;
    }

    public int getSshPort() {
        return getActiveProfile().sshPort;
    }

    public int getSshTimeoutMillis() {
        return getActiveProfile().sshTimeoutMillis;
    }

    public String getTsoAccount() {
        return getActiveProfile().tsoAccount;
    }

    public String getPassword() {
        return getPasswordForProfile(getActiveProfile().id);
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
}
