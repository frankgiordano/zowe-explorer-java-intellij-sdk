package org.zowe.explorerjava;

import java.util.Objects;
import java.util.UUID;

public final class ConnectionProfile {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String host = "";
    public int port = 443;
    public String user = "";
    public int sshPort = 22;
    public int sshTimeoutMillis = 30000;
    public String tsoAccount = "";

    public ConnectionProfile() {
    }

    public ConnectionProfile(String id, String name, String host, int port, String user, int sshPort, int sshTimeoutMillis, String tsoAccount) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "";
        this.host = host != null ? host : "";
        this.port = port > 0 ? port : 443;
        this.user = user != null ? user : "";
        this.sshPort = sshPort > 0 ? sshPort : 22;
        this.sshTimeoutMillis = sshTimeoutMillis > 0 ? sshTimeoutMillis : 30000;
        this.tsoAccount = tsoAccount != null ? tsoAccount : "";
    }

    public ConnectionProfile copy() {
        return new ConnectionProfile(id, name, host, port, user, sshPort, sshTimeoutMillis, tsoAccount);
    }

    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (!user.isBlank() || !host.isBlank()) {
            return (user.isBlank() ? "user" : user) + "@" + (host.isBlank() ? "host" : host);
        }
        return "Unnamed Connection";
    }

    @Override
    public String toString() {
        if (name != null && !name.isBlank()) {
            if (!user.isBlank() && !host.isBlank()) {
                return name + " (" + user + "@" + host + ")";
            }
            return name;
        }
        if (!user.isBlank() || !host.isBlank()) {
            return user + "@" + host + ":" + port;
        }
        return "Unnamed Connection";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionProfile that = (ConnectionProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
