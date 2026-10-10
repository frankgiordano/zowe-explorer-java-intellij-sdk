package org.zowe.explorerjava;

import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.zosmfinfo.methods.ZosmfSystems;
import zowe.client.sdk.zosmfinfo.model.DefinedSystem;
import zowe.client.sdk.zosmfinfo.response.ZosmfSystemsResponse;
import zowe.client.sdk.zosvariables.input.factory.VariableGetInputData;
import zowe.client.sdk.zosvariables.input.factory.VariableGetInputFactory;
import zowe.client.sdk.zosvariables.methods.VariableCreate;
import zowe.client.sdk.zosvariables.methods.VariableDelete;
import zowe.client.sdk.zosvariables.methods.VariableGet;
import zowe.client.sdk.zosvariables.model.SystemVariable;
import zowe.client.sdk.zosvariables.response.VariableGetResponse;
import zowe.client.sdk.zosvariables.response.VariableResponse;

import java.util.Collections;
import java.util.List;

/**
 * Explorer-facing facade for z/OS System Symbols and z/OSMF System Variables.
 * <p>
 * UI code intentionally does not call the SDK directly.
 */
public final class SymbolService {

    private final ZosConnection connection;
    private final VariableGet variableGet;
    private final VariableCreate variableCreate;
    private final VariableDelete variableDelete;
    private final ZosmfSystems zosmfSystems;

    public SymbolService(final ZosConnection connection) {
        this.connection = connection;
        this.variableGet = new VariableGet(connection);
        this.variableCreate = new VariableCreate(connection);
        this.variableDelete = new VariableDelete(connection);
        this.zosmfSystems = new ZosmfSystems(connection);
    }

    /**
     * Lists defined systems from z/OSMF topology.
     */
    public List<DefinedSystem> listDefinedSystems() throws ZosmfRequestException {
        try {
            ZosmfSystemsResponse response = zosmfSystems.get();
            if (response != null && response.getDefinedSystems() != null) {
                return List.of(response.getDefinedSystems());
            }
        } catch (Exception ex) {
            // If topology API is disabled or fails, return empty list
        }
        return Collections.emptyList();
    }

    /**
     * Retrieves z/OS System Symbols or z/OSMF System Variables.
     *
     * @param sysplexName sysplex name or null/blank for local
     * @param systemName  system name or null/blank for local
     * @param isSymbol    true for symbols, false for variables
     */
    public List<VariableResponse> getSymbolsOrVariables(
            final String sysplexName,
            final String systemName,
            final boolean isSymbol)
            throws ZosmfRequestException {

        boolean isLocal = sysplexName == null || sysplexName.isBlank()
                || systemName == null || systemName.isBlank();

        VariableGetInputData input;
        if (isSymbol) {
            input = isLocal
                    ? VariableGetInputFactory.createZosmfSymbolLocalInput()
                    : VariableGetInputFactory.createZosmfSymbolInput(sysplexName, systemName);
        } else {
            input = isLocal
                    ? VariableGetInputFactory.createZosVariableLocalInput()
                    : VariableGetInputFactory.createZosVariableInput(sysplexName, systemName);
        }

        VariableGetResponse response = variableGet.get(input);
        if (response == null) {
            return Collections.emptyList();
        }

        List<VariableResponse> result = isSymbol
                ? response.getSystemSymbolList()
                : response.getSystemVariableList();

        return result != null ? result : Collections.emptyList();
    }

    /**
     * Creates or updates a system variable.
     */
    public void createOrUpdateVariable(
            final String sysplexName,
            final String systemName,
            final String name,
            final String value,
            final String description)
            throws ZosmfRequestException {

        SystemVariable sysVar = new SystemVariable(name, value, description != null ? description : "");
        variableCreate.create(sysplexName, systemName, List.of(sysVar));
    }

    /**
     * Deletes variables by name.
     */
    public void deleteVariables(
            final String sysplexName,
            final String systemName,
            final List<String> variableNames)
            throws ZosmfRequestException {

        variableDelete.delete(sysplexName, systemName, variableNames);
    }
}
