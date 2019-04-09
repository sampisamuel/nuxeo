/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Funsho David
 */

package org.nuxeo.ecm.platform.thumbnail.processor;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_DOCUMENT_ID;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_REPOSITORY;
import static org.nuxeo.ecm.platform.thumbnail.listener.UpdateThumbnailListener.THUMBNAIL_UPDATED;
import static org.nuxeo.runtime.stream.StreamServiceImpl.DEFAULT_CODEC;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableCoreSession;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.thumbnail.ThumbnailAdapter;
import org.nuxeo.ecm.core.api.versioning.VersioningService;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.event.async.EventRecord;
import org.nuxeo.ecm.platform.dublincore.listener.DublinCoreListener;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.thumbnail.ThumbnailConstants;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.codec.CodecService;
import org.nuxeo.runtime.stream.StreamProcessorTopology;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 11.1
 */
public class UpdateThumbnailProcessor implements StreamProcessorTopology {

    private static final Log log = LogFactory.getLog(UpdateThumbnailProcessor.class);

    public static final String COMPUTATION_NAME = "UpdateThumbnail";

    public static final String STREAM_NAME = "updateThumbnail";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(() -> new UpdateThumbnailComputation(COMPUTATION_NAME),
                               Collections.singletonList("i1:" + STREAM_NAME))
                       .build();
    }

    public static class UpdateThumbnailComputation extends AbstractComputation {

        public UpdateThumbnailComputation(String name) {
            super(name, 1, 0);
        }

        @Override
        public void processRecord(ComputationContext context, String inputStreamName, Record record) {
            TransactionHelper.runInNewTransaction(() -> {
                EventRecord eventRecord = Framework.getService(CodecService.class)
                                                   .getCodec(DEFAULT_CODEC, EventRecord.class)
                                                   .decode(record.getData());
                if (!ThumbnailConstants.EventNames.scheduleThumbnailUpdate.name().equals(eventRecord.getName())) {
                    return; // TODO : add an event filter (same as event transformer) at routing ?
                }
                LoginContext loginContext;
                try {
                    String username = eventRecord.getUsername();
                    loginContext = "system".equals(username) ? Framework.login()
                            : Framework.loginAsUser(eventRecord.getUsername());
                    String documentId = eventRecord.getContext().get(EVENT_CONTEXT_DOCUMENT_ID);
                    try (CloseableCoreSession session = CoreInstance.openCoreSession(
                            eventRecord.getContext().get(EVENT_CONTEXT_REPOSITORY))) {
                        DocumentModel documentModel = session.getDocument(new IdRef(documentId));
                        processDocument(session, documentModel);
                    } catch (Exception e) {
                        log.error("Cannot process document : " + documentId, e);
                    } finally {
                        if (loginContext != null) {
                            loginContext.logout();
                        }
                    }
                } catch (LoginException e) {
                    log.error("Login exception ", e);
                }
            });
            context.askForCheckpoint();
        }

        protected void processDocument(CoreSession session, DocumentModel doc) {
            Blob thumbnailBlob = getManagedThumbnail(doc);
            if (thumbnailBlob == null) {
                ThumbnailAdapter thumbnailAdapter = doc.getAdapter(ThumbnailAdapter.class);
                if (thumbnailAdapter == null) {
                    return;
                }
                thumbnailBlob = thumbnailAdapter.computeThumbnail(session);
            }
            if (thumbnailBlob != null) {
                if (!doc.hasFacet(ThumbnailConstants.THUMBNAIL_FACET)) {
                    doc.addFacet(ThumbnailConstants.THUMBNAIL_FACET);
                }
                doc.setPropertyValue(ThumbnailConstants.THUMBNAIL_PROPERTY_NAME, (Serializable) thumbnailBlob);
            } else {
                if (doc.hasFacet(ThumbnailConstants.THUMBNAIL_FACET)) {
                    doc.setPropertyValue(ThumbnailConstants.THUMBNAIL_PROPERTY_NAME, null);
                    doc.removeFacet(ThumbnailConstants.THUMBNAIL_FACET);
                }
            }
            if (doc.isDirty()) {
                doc.putContextData(VersioningService.VERSIONING_OPTION, VersioningOption.NONE);
                doc.putContextData(VersioningService.DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
                doc.putContextData(DublinCoreListener.DISABLE_DUBLINCORE_LISTENER, Boolean.TRUE);
                doc.putContextData(NotificationConstants.DISABLE_NOTIFICATION_SERVICE, Boolean.TRUE);
                doc.putContextData("disableAuditLogger", Boolean.TRUE);
                if (doc.isVersion()) {
                    doc.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
                }
                doc.putContextData(THUMBNAIL_UPDATED, true);
                session.saveDocument(doc);
            }
        }

        protected Blob getManagedThumbnail(DocumentModel doc) {
            BlobHolder bh = doc.getAdapter(BlobHolder.class);
            if (bh == null) {
                return null;
            }
            Blob blob = bh.getBlob();
            if (blob == null) {
                return null;
            }
            BlobManager blobManager = Framework.getService(BlobManager.class);
            try {
                InputStream is = blobManager.getThumbnail(blob);
                if (is == null) {
                    return null;
                }
                return Blobs.createBlob(is);
            } catch (IOException e) {
                throw new NuxeoException("Failed to get managed blob thumbnail", e);
            }
        }
    }
}
