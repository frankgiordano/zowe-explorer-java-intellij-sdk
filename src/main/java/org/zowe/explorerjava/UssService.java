package org.zowe.explorerjava;

import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.zosfiles.uss.input.UssListInputData;
import zowe.client.sdk.zosfiles.uss.methods.UssGet;
import zowe.client.sdk.zosfiles.uss.methods.UssList;
import zowe.client.sdk.zosfiles.uss.methods.UssWrite;
import zowe.client.sdk.zosfiles.uss.model.UnixFile;

import java.util.List;

/** Explorer-facing USS facade backed entirely by Zowe Client Java SDK. */
public final class UssService {
    private final UssList list;
    private final UssGet get;
    private final UssWrite write;

    public UssService(ZosConnection connection) {
        this.list = new UssList(connection);
        this.get = new UssGet(connection);
        this.write = new UssWrite(connection);
    }

    public List<UnixFile> list(String path) throws ZosmfRequestException {
        UssListInputData input = new UssListInputData.Builder().path(path).build();
        return list.getFiles(input);
    }

    public String readText(String path) throws ZosmfRequestException {
        return get.getText(path);
    }

    public void writeText(String path, String content) throws ZosmfRequestException {
        write.writeText(path, content);
    }
}
