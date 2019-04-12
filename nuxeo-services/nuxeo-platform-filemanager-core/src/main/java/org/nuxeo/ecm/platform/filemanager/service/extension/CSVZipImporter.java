/*
 * (C) Copyright 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *     Jackie Aldama <jaldama@nuxeo.com>
 *
 */
package org.nuxeo.ecm.platform.filemanager.service.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.IdUtils;
import org.nuxeo.common.utils.Path;
import org.nuxeo.ecm.automation.core.util.ComplexTypeJSONDecoder;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.TypeConstants;
import org.nuxeo.ecm.core.schema.types.*;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.IntegerType;
import org.nuxeo.ecm.core.schema.types.primitives.LongType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.platform.filemanager.api.FileImporterContext;
import org.nuxeo.runtime.api.Framework;

public class CSVZipImporter extends AbstractFileImporter {

    private static final long serialVersionUID = 1L;

    private static final String MARKER = "meta-data.csv";

    public static final String NUXEO_CSV_BLOBS_FOLDER = "nuxeo.csv.blobs.folder";

    private static final Log log = LogFactory.getLog(CSVZipImporter.class);

    public static ZipFile getArchiveFileIfValid(File file) throws IOException {
        ZipFile zip;

        try {
            zip = new ZipFile(file);
        } catch (ZipException e) {
            log.debug("file is not a zipfile ! ", e);
            return null;
        } catch (IOException e) {
            log.debug("can not open zipfile ! ", e);
            return null;
        }

        ZipEntry marker = zip.getEntry(MARKER);

        if (marker == null) {
            zip.close();
            return null;
        } else {
            return zip;
        }
    }

    @Override
    public boolean isOneToMany() {
        return true;
    }

    @Override
    public DocumentModel createOrUpdate(FileImporterContext context) throws IOException {
        CoreSession session = context.getSession();
        Blob blob = context.getBlob();
        ZipFile zip;
        try (CloseableFile source = blob.getCloseableFile()) {
            zip = getArchiveFileIfValid(source.getFile());
            if (zip == null) {
                return null;
            }

            String parentPath = context.getParentPath();
            DocumentModel container = session.getDocument(new PathRef(parentPath));

            ZipEntry index = zip.getEntry(MARKER);
            try (Reader reader = new InputStreamReader(zip.getInputStream(index));
                 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader());) {

                Map<String, Integer> header = csvParser.getHeaderMap();
                for (CSVRecord csvRecord : csvParser) {
                    String type = null;
                    String name = null;
                    Map<String, String> stringValues = new HashMap<>();
                    for (String headerValue : header.keySet()) {
                        String lineValue = csvRecord.get(headerValue);
                        if ("type".equalsIgnoreCase(headerValue)) {
                            type = lineValue;
                        } else if ("name".equalsIgnoreCase(headerValue)) {
                            name = lineValue;
                        } else {
                            stringValues.put(headerValue, lineValue);
                        }
                    }

                    if (name == null || name.isEmpty()) {
                        log.error("Can not create or update doc without a name, skipping line");
                        continue;
                    }

                    boolean updateDoc = false;
                    // get doc for update
                    DocumentModel targetDoc = null;

                    String targetPath = new Path(parentPath).append(name).toString();
                    if (session.exists(new PathRef(targetPath))) {
                        targetDoc = session.getDocument(new PathRef(targetPath));
                        updateDoc = true;
                    }

                    // create doc if needed
                    if (targetDoc == null) {
                        if (type == null) {
                            log.error("Can not create doc without a type, skipping line");
                            continue;
                        }
                        targetDoc = session.createDocumentModel(parentPath, name, type);
                    }

                    // update doc properties
                    DocumentType targetDocType = targetDoc.getDocumentType();
                    for (Map.Entry<String, String> entry : stringValues.entrySet()) {
                        String fname = entry.getKey();
                        String stringValue = entry.getValue();
                        Field field = null;
                        boolean usePrefix = false;
                        String schemaName = null;
                        String fieldName = null;

                        if (fname.contains(":")) {
                            if (targetDocType.hasField(fname)) {
                                field = targetDocType.getField(fname);
                                usePrefix = true;
                            }
                        } else if (fname.contains(".")) {
                            String[] parts = fname.split("\\.");
                            schemaName = parts[0];
                            fieldName = parts[1];
                            if (targetDocType.hasSchema(schemaName)) {
                                field = targetDocType.getField(fieldName);
                                usePrefix = false;
                            }
                        } else {
                            if (targetDocType.hasField(fname)) {
                                field = targetDocType.getField(fname);
                                usePrefix = false;
                                schemaName = field.getDeclaringType().getSchemaName();
                            }
                        }

                        if (field != null) {
                            Serializable fieldValue = getFieldValue(field, stringValue, zip);

                            if (fieldValue != null) {
                                if (usePrefix) {
                                    targetDoc.setPropertyValue(fname, fieldValue);
                                } else {
                                    targetDoc.setProperty(schemaName, fieldName, fieldValue);
                                }
                            }
                        }
                    }
                    if (updateDoc) {
                        session.saveDocument(targetDoc);
                    } else {
                        session.createDocument(targetDoc);
                    }
                }
            }
            return container;
        }
    }

    protected Serializable getFieldValue(Field field, String stringValue, ZipFile zip) {
        Serializable fieldValue = null;
        Type type = field.getType();
        if (type.isSimpleType()) {
            if (type instanceof SimpleTypeImpl) {
                // consider super type instead
                type = type.getSuperType();
            }
            if (type instanceof StringType) {
                fieldValue = stringValue;
            } else if (type instanceof IntegerType) {
                fieldValue = Integer.parseInt(stringValue);
            } else if (type instanceof LongType) {
                fieldValue = Long.parseLong(stringValue);
            } else if (type instanceof DateType) {
                try {
                    Date date;
                    if (stringValue.length() == 10) {
                        date = new SimpleDateFormat("dd/MM/yyyy").parse(stringValue);
                    } else if (stringValue.length() == 8) {
                        date = new SimpleDateFormat("dd/MM/yy").parse(stringValue);
                    } else {
                        log.warn("Unknown date format :" + stringValue);
                        return null;
                    }
                    fieldValue = date;
                } catch (ParseException e) {
                    log.error("Error during date parsing", e);
                }
            } else {
                log.warn(String.format("Unsupported field type '%s'", type));
                return null;
            }
        } else if (type.isComplexType()) {
            if (TypeConstants.CONTENT.equals(field.getName().getLocalName())) {
                ZipEntry blobIndex = zip.getEntry(stringValue);
                if (blobIndex != null) {
                    Blob blob;
                    try {
                        blob = Blobs.createBlob(zip.getInputStream(blobIndex));
                    } catch (IOException e) {
                        throw new NuxeoException(e);
                    }
                    blob.setFilename(stringValue);
                    fieldValue = (Serializable) blob;
                }
            } else {
                try {
                    fieldValue = (Serializable) ComplexTypeJSONDecoder.decode((ComplexType) type,
                        stringValue);
                    replaceBlobs((Map<String, Object>) fieldValue);
                } catch (Exception e) {
                    throw new NuxeoException(e);
                }
            }
        } else if (type.isListType()) {
            Type listFieldType = ((ListType) type).getFieldType();
            if (listFieldType.isSimpleType()) {
                /*
                 * Array.
                 */
                fieldValue = stringValue.split("\\|");
            } else {
                /*
                 * TODO:
                 * Complex list.
                 */
                try {
                    fieldValue = (Serializable) ComplexTypeJSONDecoder.decodeList((ListType) listFieldType, stringValue);
                    replaceBlobs((List<Object>) fieldValue);
                } catch (IOException e) {
                    log.error(e.getStackTrace());
                }
            }
        }

        return fieldValue;
    }

    /**
     * Creates a {@code Blob} from a relative file path. The File will be looked up in the folder registered by the
     * {@code nuxeo.csv.blobs.folder} property.
     *
     * @since 9.3
     */
    protected Blob createBlobFromFilePath(String fileRelativePath) throws IOException {
        String blobsFolderPath = Framework.getProperty(NUXEO_CSV_BLOBS_FOLDER);
        String path = FilenameUtils.normalize(blobsFolderPath + "/" + fileRelativePath);
        File file = new File(path);
        if (file.exists()) {
            return Blobs.createBlob(file, null, null, FilenameUtils.getName(fileRelativePath));
        } else {
            return null;
        }
    }

    /**
     * Creates a {@code Blob} from a {@code StringBlob}. Assume that the {@code StringBlob} content is the relative file
     * path. The File will be looked up in the folder registered by the {@code nuxeo.csv.blobs.folder} property.
     *
     * @since 9.3
     */
    protected Blob createBlobFromStringBlob(Blob stringBlob) throws IOException {
        String fileRelativePath = stringBlob.getString();
        Blob blob = createBlobFromFilePath(fileRelativePath);
        if (blob == null) {
            throw new IOException(String.format("File %s does not exist", fileRelativePath));
        }

        blob.setMimeType(stringBlob.getMimeType());
        blob.setEncoding(stringBlob.getEncoding());
        String filename = stringBlob.getFilename();
        if (filename != null) {
            blob.setFilename(filename);
        }
        return blob;
    }

    /**
     * Recursively replaces all {@code Blob}s with {@code Blob}s created from Files stored in the folder registered by
     * the {@code nuxeo.csv.blobs.folder} property.
     *
     * @since 9.3
     */
    @SuppressWarnings("unchecked")
    protected void replaceBlobs(Map<String, Object> map) throws IOException {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Blob) {
                Blob blob = (Blob) value;
                entry.setValue(createBlobFromStringBlob(blob));
            } else if (value instanceof List) {
                replaceBlobs((List<Object>) value);
            } else if (value instanceof Map) {
                replaceBlobs((Map<String, Object>) value);
            }
        }
    }

    /**
     * Recursively replaces all {@code Blob}s with {@code Blob}s created from Files stored in the folder registered by
     * the {@code nuxeo.csv.blobs.folder} property.
     *
     * @since 9.3
     */
    @SuppressWarnings("unchecked")
    protected void replaceBlobs(List<Object> list) throws IOException {
        for (ListIterator<Object> it = list.listIterator(); it.hasNext();) {
            Object value = it.next();
            if (value instanceof Blob) {
                Blob blob = (Blob) value;
                it.set(createBlobFromStringBlob(blob));
            } else if (value instanceof List) {
                replaceBlobs((List<Object>) value);
            } else if (value instanceof Map) {
                replaceBlobs((Map<String, Object>) value);
            }
        }
    }
}
