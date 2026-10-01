package org.zowe.explorerjava;

import zowe.client.sdk.core.SshConnection;
import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.zosconsole.methods.ConsoleCmd;
import zowe.client.sdk.zosconsole.response.ConsoleCmdResponse;
import zowe.client.sdk.zostso.input.StartTsoInputData;
import zowe.client.sdk.zostso.methods.TsoCmd;
import zowe.client.sdk.zostso.methods.TsoStart;
import zowe.client.sdk.zostso.methods.TsoStop;
import zowe.client.sdk.zostso.response.TsoStartResponse;
import zowe.client.sdk.zosuss.method.UssCmd;

import java.util.List;

/**
 * Command facade for TSO, MVS console, and USS SSH command execution.
 * <p>
 * TSO sessions are explicitly started and kept alive so repeated commands use
 * TsoCmd.issueCommandByTsoSessionId instead of creating a new address space per command.
 */
public final class CommandService implements AutoCloseable {
    private final ZosConnection zosConnection;
    private final String tsoAccount;
    private final SshConnection sshConnection;
    private final int sshTimeoutMillis;

    private TsoCmd tsoCmd;
    private String tsoSessionId;

    public CommandService(ZosConnection zosConnection,
                          String tsoAccount,
                          SshConnection sshConnection,
                          int sshTimeoutMillis) {
        this.zosConnection = zosConnection;
        this.tsoAccount = tsoAccount;
        this.sshConnection = sshConnection;
        this.sshTimeoutMillis = sshTimeoutMillis;
    }

    public synchronized String startTsoSession() throws Exception {
        if (isTsoSessionActive()) return tsoSessionId;
        if (tsoAccount == null || tsoAccount.isBlank()) {
            throw new IllegalStateException("Configure a TSO account number first.");
        }

        StartTsoInputData input = new StartTsoInputData();
        input.setAccount(tsoAccount.trim());
        TsoStartResponse startResponse = new TsoStart(zosConnection).start(input);
        if (!startResponse.isSuccess()) {
            throw new IllegalStateException("TSO logon did not complete successfully: " + startResponse.getResponse());
        }

        tsoSessionId = startResponse.getSessionId();
        tsoCmd = new TsoCmd(zosConnection, tsoAccount.trim());
        // Drain READY/logon noise exactly as TsoCmd.issueCommand does before the user command.
        tsoCmd.drainLogonPrompt(startResponse);
        return tsoSessionId;
    }

    public synchronized List<String> issueTso(String command) throws Exception {
        if (command == null || command.isBlank()) throw new IllegalArgumentException("TSO command is required.");
        String session = startTsoSession();
        return tsoCmd.issueCommandByTsoSessionId(session, command.trim());
    }

    public synchronized void stopTsoSession() throws Exception {
        if (!isTsoSessionActive()) return;
        try {
            new TsoStop(zosConnection).stop(tsoSessionId);
        } finally {
            tsoSessionId = null;
            tsoCmd = null;
        }
    }

    public synchronized boolean isTsoSessionActive() {
        return tsoSessionId != null && !tsoSessionId.isBlank();
    }

    public synchronized String getTsoSessionId() {
        return tsoSessionId;
    }

    public String issueConsole(String command) throws Exception {
        if (command == null || command.isBlank()) throw new IllegalArgumentException("Console command is required.");
        ConsoleCmdResponse response = new ConsoleCmd(zosConnection).issueCommand(command.trim());
        String output = response.getCmdResponse();
        return output == null || output.isBlank() ? response.toString() : output;
    }

    public String issueUss(String command) throws Exception {
        if (command == null || command.isBlank()) throw new IllegalArgumentException("USS command is required.");
        return new UssCmd(sshConnection).issueCommand(command.trim(), sshTimeoutMillis);
    }

    @Override
    public void close() {
        try {
            stopTsoSession();
        } catch (Exception ignored) {
            // Best-effort cleanup during IDE/project disposal.
        }
    }
}
