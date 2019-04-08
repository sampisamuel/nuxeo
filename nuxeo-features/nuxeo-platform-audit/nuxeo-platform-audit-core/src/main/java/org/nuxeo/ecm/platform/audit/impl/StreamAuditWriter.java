/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.ecm.platform.audit.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static org.nuxeo.ecm.core.api.LifeCycleConstants.TRANSTION_EVENT_OPTION_TO;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_DOCUMENT_FACETS;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_DOCUMENT_ID;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_DOCUMENT_LIFECYCLE_STATE;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_DOCUMENT_PATH;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_DOCUMENT_TYPE;
import static org.nuxeo.ecm.core.event.async.EventTransformer.EVENT_CONTEXT_REPOSITORY;
import static org.nuxeo.ecm.core.schema.FacetNames.SYSTEM_DOCUMENT;
import static org.nuxeo.ecm.platform.audit.listener.StreamAuditEventListener.STREAM_NAME;
import static org.nuxeo.ecm.platform.audit.service.AbstractAuditBackend.FORCE_AUDIT_FACET;
import static org.nuxeo.ecm.platform.audit.service.NXAuditEventsService.DISABLE_AUDIT_LOGGER;
import static org.nuxeo.ecm.platform.audit.transformer.AuditEventTransformer.EVENT_CONTEXT_CATEGORY;
import static org.nuxeo.ecm.platform.audit.transformer.AuditEventTransformer.EVENT_CONTEXT_COMMENT;
import static org.nuxeo.ecm.platform.audit.transformer.AuditEventTransformer.EVENT_CONTEXT_DELETED_DOCUMENT;
import static org.nuxeo.runtime.stream.StreamServiceImpl.DEFAULT_CODEC;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.event.async.EventRecord;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.io.ExtendedInfoDeserializer;
import org.nuxeo.ecm.platform.audit.service.NXAuditEventsService;
import org.nuxeo.ecm.platform.audit.service.extension.ExtendedInfoDescriptor;
import org.nuxeo.lib.stream.computation.AbstractBatchComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.codec.CodecService;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Computation that consumes a stream of log entries and write them to the audit backend.
 *
 * @since 9.3
 */
public class StreamAuditWriter implements StreamProcessorTopology {
    private static final Log log = LogFactory.getLog(StreamAuditWriter.class);

    public static final String COMPUTATION_NAME = "AuditLogWriter";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(
                               () -> new AuditLogWriterComputation(COMPUTATION_NAME),
                               Collections.singletonList("i1:" + STREAM_NAME))
                       .build();
    }

    public static class AuditLogWriterComputation extends AbstractBatchComputation {

        protected ObjectMapper objectMapper;

        public AuditLogWriterComputation(String name) {
            super(name, 1, 0);
            objectMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addDeserializer(ExtendedInfo.class, new ExtendedInfoDeserializer());
            objectMapper.registerModule(module);
        }

        @Override
        public void batchProcess(ComputationContext context, String inputStreamName, List<Record> records) {
            List<LogEntry> logEntries = new ArrayList<>(records.size());
            for (Record record : records) {
                try {
                    // logEntries.add(getLogEntryFromJson(record.getData()));
                    EventRecord eventRecord = Framework.getService(CodecService.class)
                                                       .getCodec(DEFAULT_CODEC, EventRecord.class)
                                                       .decode(record.getData());
                    logEntries.add(getLogEntryFromEventRecord(eventRecord));
                } catch (NuxeoException e) {
                    log.error("Discard invalid record: " + record, e);
                }
            }
            writeEntriesToAudit(logEntries);
        }

        @Override
        public void batchFailure(ComputationContext context, String inputStreamName, List<Record> records) {
            // error log already done by abstract
        }

        protected void writeEntriesToAudit(List<LogEntry> logEntries) {
            if (logEntries.isEmpty()) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(String.format("Writing %d log entries to audit backend.", logEntries.size()));
            }
            AuditLogger logger = Framework.getService(AuditLogger.class);
            logger.addLogEntries(logEntries);
        }

        protected LogEntry getLogEntryFromEventRecord(EventRecord eventRecord) {
            LogEntry entry = new LogEntryImpl();
            entry.setEventId(eventRecord.getName());
            Date eventDate = new Date(eventRecord.getTime());
            entry.setEventDate(eventDate);
            entry.setPrincipalName(eventRecord.getUsername());

            Map<String, String> eventContext = eventRecord.getContext();
            entry.setComment(eventContext.get(EVENT_CONTEXT_COMMENT));
            String category = eventContext.get(EVENT_CONTEXT_CATEGORY);


            if (eventContext.containsKey(EVENT_CONTEXT_DOCUMENT_ID)) {
                Set<String> facets = Set.of(eventContext.get(EVENT_CONTEXT_DOCUMENT_FACETS).split(","));
                if (facets.contains(SYSTEM_DOCUMENT) && !facets.contains(FORCE_AUDIT_FACET)) {
                    // do not log event on System documents
                    // unless it has the FORCE_AUDIT_FACET facet
                    return null;
                }

                boolean disabled = Boolean.parseBoolean(eventContext.get(DISABLE_AUDIT_LOGGER));
                if (disabled) {
                    // don't log events with this flag
                    return null;
                }
                entry.setDocUUID(eventContext.get(EVENT_CONTEXT_DOCUMENT_ID));
                entry.setDocPath(eventContext.get(EVENT_CONTEXT_DOCUMENT_PATH));
                entry.setDocType(eventContext.get(EVENT_CONTEXT_DOCUMENT_TYPE));
                entry.setRepositoryId(eventContext.get(EVENT_CONTEXT_REPOSITORY));

                if (Boolean.valueOf(eventContext.get(EVENT_CONTEXT_DELETED_DOCUMENT))) {
                    entry.setComment("Document does not exist anymore!");
                } else {
                    ofNullable(eventContext.get(EVENT_CONTEXT_DOCUMENT_LIFECYCLE_STATE)).ifPresent(
                            entry::setDocLifeCycle);
                }
                if (LifeCycleConstants.TRANSITION_EVENT.equals(eventRecord.getName())) {
                    entry.setDocLifeCycle(eventContext.get(TRANSTION_EVENT_OPTION_TO));
                }
                if (category == null) {
                    category = "eventDocumentCategory";
                }
                entry.setCategory(category);
            }
            Map<String, ExtendedInfo> extendedInfos = new HashMap<>();

            NXAuditEventsService auditService = (NXAuditEventsService) Framework.getRuntime()
                                                                                .getComponent(NXAuditEventsService.NAME);
            Set<ExtendedInfoDescriptor> descriptors = auditService.getExtendedInfoDescriptors();
            List<ExtendedInfoDescriptor> eventDescriptors = auditService.getEventExtendedInfoDescriptors()
                                                                        .get(eventRecord.getName());
            if (eventDescriptors != null && !eventDescriptors.isEmpty()) {
                descriptors.addAll(eventDescriptors);
            }

            for (ExtendedInfoDescriptor desc : descriptors) {
                String key = desc.getKey();
                String value = eventContext.get(key);
                if (value != null) {
                    ExtendedInfo info = objectMapper.convertValue(value, ExtendedInfo.class);
                    extendedInfos.put(key, info);
                }
            }
            entry.setExtendedInfos(extendedInfos);

            return entry;
        }
    }

}
