package org.zowe.explorerjava;

import zowe.client.sdk.core.SshConnection;
import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.core.ZosConnectionFactory;

public final class ZoweConnectionProvider {
    private ZoweConnectionProvider() {}

    public static ZosConnection current() {
        ZoweConnectionSettings s = ZoweConnectionSettings.getInstance();
        validate(s);
        return ZosConnectionFactory.createBasicConnection(
                s.getHost(), s.getPort(), s.getUser(), s.getPassword());
    }

    public static SshConnection currentSsh() {
        ZoweConnectionSettings s = ZoweConnectionSettings.getInstance();
        validate(s);
        return new SshConnection(s.getHost(), s.getSshPort(), s.getUser(), s.getPassword());
    }

    public static CommandService currentCommandService() {
        ZoweConnectionSettings s = ZoweConnectionSettings.getInstance();
        return new CommandService(current(), s.getTsoAccount(), currentSsh(), s.getSshTimeoutMillis());
    }

    private static void validate(ZoweConnectionSettings s) {
        if (s.getHost().isBlank() || s.getUser().isBlank()) {
            throw new IllegalStateException("Configure a z/OS connection first.");
        }
    }
}
