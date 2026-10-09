package org.zowe.explorerjava;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.GetJsonZosmfRequest;
import zowe.client.sdk.rest.GetTextZosmfRequest;
import zowe.client.sdk.rest.Response;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.utility.EncodeUtils;
import zowe.client.sdk.utility.FileUtils;
import zowe.client.sdk.zosfiles.ZosFilesConstants;
import zowe.client.sdk.zosfiles.uss.input.UssCreateInputData;
import zowe.client.sdk.zosfiles.uss.input.UssListInputData;
import zowe.client.sdk.zosfiles.uss.input.UssWriteInputData;
import zowe.client.sdk.zosfiles.uss.methods.UssChangeTag;
import zowe.client.sdk.zosfiles.uss.methods.UssCreate;
import zowe.client.sdk.zosfiles.uss.methods.UssDelete;
import zowe.client.sdk.zosfiles.uss.methods.UssGet;
import zowe.client.sdk.zosfiles.uss.methods.UssList;
import zowe.client.sdk.zosfiles.uss.methods.UssMove;
import zowe.client.sdk.zosfiles.uss.methods.UssWrite;
import zowe.client.sdk.zosfiles.uss.model.UnixFile;
import zowe.client.sdk.zosfiles.uss.response.UnixFileListResponse;
import zowe.client.sdk.zosfiles.uss.types.CreateType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Explorer-facing USS facade backed entirely by Zowe Client Java SDK.
 */
public final class UssService {
    private static final ObjectMapper LENIENT_MAPPER = new ObjectMapper()
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ZosConnection connection;
    private final UssList list;
    private final UssGet get;
    private final UssWrite write;
    private final UssCreate create;
    private final UssDelete delete;
    private final UssMove move;
    private final UssChangeTag tag;

    public UssService(ZosConnection connection) {
        this.connection = connection;
        this.list = new UssList(connection);
        this.get = new UssGet(connection);
        this.write = new UssWrite(connection);
        this.create = new UssCreate(connection);
        this.delete = new UssDelete(connection);
        this.move = new UssMove(connection);
        this.tag = new UssChangeTag(connection);
    }

    public List<UnixFile> list(String path) throws ZosmfRequestException {
        return list(path, null);
    }

    public List<UnixFile> list(String path, String namePattern) throws ZosmfRequestException {
        UssListInputData.Builder builder = new UssListInputData.Builder().path(path);
        if (namePattern != null && !namePattern.isBlank()) {
            builder.name(namePattern.trim());
        }
        UssListInputData input = builder.build();
        List<UnixFile> files;
        try {
            files = list.getFiles(input);
        } catch (ZosmfRequestException ex) {
            if (isControlCharParseException(ex)) {
                files = getFilesLenient(input);
            } else {
                throw ex;
            }
        }
        if (files == null) {
            return new ArrayList<>();
        }
        List<UnixFile> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(Comparator
                .comparing((UnixFile f) -> !isDirectory(f))
                .thenComparing(f -> f.getName() != null ? f.getName() : "", String.CASE_INSENSITIVE_ORDER)
                .thenComparing(f -> f.getName() != null ? f.getName() : ""));
        return sortedFiles;
    }

    private static boolean isControlCharParseException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("CTRL-CHAR") || msg.contains("Illegal unquoted character") || msg.contains("ALLOW_UNESCAPED_CONTROL_CHARS"))) {
            return true;
        }
        return isControlCharParseException(ex.getCause());
    }

    private List<UnixFile> getFilesLenient(UssListInputData listInputData) throws ZosmfRequestException {
        String path = listInputData.getPath().orElseThrow(() -> new IllegalArgumentException("path not specified"));
        String urlStart = connection.getZosmfUrl() + ZosFilesConstants.RESOURCE + ZosFilesConstants.RES_USS_FILES;
        StringBuilder url = new StringBuilder(urlStart);
        url.append("?path=").append(EncodeUtils.encodeURIComponent(FileUtils.validatePath(path)));
        listInputData.getName().ifPresent(name ->
                url.append("&name=").append(EncodeUtils.encodeURIComponent(name)));

        GetJsonZosmfRequest request = new GetJsonZosmfRequest(connection);
        request.setUrl(url.toString());

        Response response = request.executeRequest();
        String jsonPhrase = response.getResponsePhraseAsString().orElse("{}");

        try {
            UnixFileListResponse res = LENIENT_MAPPER.readValue(jsonPhrase, UnixFileListResponse.class);
            return res.getItems() == null ? List.of() : res.getItems();
        } catch (Exception e) {
            throw new ZosmfRequestException("Failed to parse USS directory listing even with lenient parser: " + e.getMessage(), e);
        }
    }

    private static boolean isDirectory(UnixFile file) {
        return file.getMode() != null && file.getMode().startsWith("d");
    }

    public String readText(String path) throws ZosmfRequestException {
        return get.getText(path);
    }

    public void writeText(String path, String content) throws ZosmfRequestException {
        write.writeText(path, content);
    }

    /**
     * Reads the file as text using an explicit code set (for example ISO8859-1), independent of the file tag.
     */
    public String readText(String path, String encoding) throws ZosmfRequestException {
        if (encoding == null || encoding.isBlank()) {
            return readText(path);
        }
        String url = connection.getZosmfUrl() + ZosFilesConstants.RESOURCE + ZosFilesConstants.RES_USS_FILES
                + EncodeUtils.encodeURIComponent(FileUtils.validatePath(path));
        GetTextZosmfRequest request = new GetTextZosmfRequest(connection);
        request.setHeaders(Map.of("X-IBM-Data-Type", "text;fileEncoding=" + encoding.trim()));
        request.setUrl(url);
        Object body = request.executeRequest().getResponsePhrase().orElse("");
        return body == null ? "" : body.toString();
    }

    public void writeText(String path, String content, String encoding) throws ZosmfRequestException {
        if (encoding == null || encoding.isBlank()) {
            writeText(path, content);
            return;
        }
        write.writeCommon(path, new UssWriteInputData.Builder()
                .textContent(content).fileEncoding(encoding.trim()).build());
    }

    /**
     * Sets the file tag (chtag). A text tag with a code set such as ISO8859-1 tells z/OSMF how to
     * convert the file to/from UTF-8 when it is read or written.
     */
    public void setTextTag(String path, String codeSet) throws ZosmfRequestException {
        tag.text(path, codeSet);
    }

    public void setBinaryTag(String path) throws ZosmfRequestException {
        tag.binary(path);
    }

    public void removeTag(String path) throws ZosmfRequestException {
        tag.remove(path);
    }

    public void createFile(String path) throws ZosmfRequestException {
        UssCreateInputData input = new UssCreateInputData(CreateType.FILE, "rw-r--r--");
        create.create(path, input);
    }

    public void createDirectory(String path) throws ZosmfRequestException {
        UssCreateInputData input = new UssCreateInputData(CreateType.DIR, "rwxr-xr-x");
        create.create(path, input);
    }

    public void delete(String path, boolean recursive) throws ZosmfRequestException {
        delete.delete(path, recursive);
    }

    public void rename(String oldPath, String newPath) throws ZosmfRequestException {
        move.move(oldPath, newPath);
    }
}
