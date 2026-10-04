package org.zowe.explorerjava;

import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.zosfiles.dsn.input.DsnDeleteInputData;
import zowe.client.sdk.zosfiles.dsn.input.DsnDownloadInputData;
import zowe.client.sdk.zosfiles.dsn.input.DsnListInputData;
import zowe.client.sdk.zosfiles.dsn.input.DsnRenameInputData;
import zowe.client.sdk.zosfiles.dsn.methods.DsnDelete;
import zowe.client.sdk.zosfiles.dsn.methods.DsnGet;
import zowe.client.sdk.zosfiles.dsn.methods.DsnList;
import zowe.client.sdk.zosfiles.dsn.methods.DsnUpdate;
import zowe.client.sdk.zosfiles.dsn.methods.DsnWrite;
import zowe.client.sdk.zosfiles.dsn.model.Dataset;
import zowe.client.sdk.zosfiles.dsn.model.Member;
import zowe.client.sdk.zosfiles.dsn.types.AttributeType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Explorer-facing data set facade backed entirely by Zowe Client Java SDK.
 */
public final class DataSetService {
    private final DsnList list;
    private final DsnGet get;
    private final DsnWrite write;
    private final DsnDelete delete;
    private final DsnUpdate update;

    public DataSetService(ZosConnection connection) {
        this.list = new DsnList(connection);
        this.get = new DsnGet(connection);
        this.write = new DsnWrite(connection);
        this.delete = new DsnDelete(connection);
        this.update = new DsnUpdate(connection);
    }

    public List<Dataset> list(String mask) throws ZosmfRequestException {
        DsnListInputData input = new DsnListInputData.Builder()
                .attribute(AttributeType.BASE)
                .build();
        return list.getDatasets(mask, input);
    }

    public List<Member> members(String dataSetName) throws ZosmfRequestException {
        DsnListInputData input = new DsnListInputData.Builder()
                .attribute(AttributeType.MEMBER)
                .build();
        return list.getMembers(dataSetName, input);
    }

    public String read(String dataSetOrMember) throws ZosmfRequestException, IOException {
        DsnDownloadInputData input = new DsnDownloadInputData.Builder().build();
        try (InputStream in = get.get(dataSetOrMember, input)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void write(String dataSetOrMember, String content) throws ZosmfRequestException {
        int open = dataSetOrMember.lastIndexOf('(');
        if (open > 0 && dataSetOrMember.endsWith(")")) {
            String dataSet = dataSetOrMember.substring(0, open);
            String member = dataSetOrMember.substring(open + 1, dataSetOrMember.length() - 1);
            write.write(dataSet, member, content);
        } else {
            write.write(dataSetOrMember, content);
        }
    }

    public void deleteMember(String dataSet, String member) throws ZosmfRequestException {
        DsnDeleteInputData input = DsnDeleteInputData.forMember(dataSet, member);
        delete.delete(input);
    }

    public void renameMember(String dataSet, String oldMember, String newMember) throws ZosmfRequestException {
        DsnRenameInputData input = DsnRenameInputData.forMember(dataSet, oldMember, newMember);
        update.rename(input);
    }

    public void createMember(String dataSet, String member) throws ZosmfRequestException {
        write.write(dataSet, member, "");
    }
}
