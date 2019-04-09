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

package org.nuxeo.ecm.platform.audit.transformer;

import static java.util.Optional.ofNullable;
import static org.nuxeo.ecm.core.api.LifeCycleConstants.TRANSTION_EVENT_OPTION_TO;
import static org.nuxeo.ecm.platform.audit.service.NXAuditEventsService.DISABLE_AUDIT_LOGGER;

import java.io.IOException;
import java.io.Serializable;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.el.ELException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.el.ExpressionFactoryImpl;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.DeletedDocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.async.EventTransformer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.io.ExtendedInfoSerializer;
import org.nuxeo.ecm.platform.audit.service.NXAuditEventsService;
import org.nuxeo.ecm.platform.audit.service.extension.AdapterDescriptor;
import org.nuxeo.ecm.platform.audit.service.extension.ExtendedInfoDescriptor;
import org.nuxeo.ecm.platform.el.ExpressionContext;
import org.nuxeo.ecm.platform.el.ExpressionEvaluator;
import org.nuxeo.runtime.api.Framework;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * @since 11.1
 */
public class AuditEventTransformer implements EventTransformer {

    private static final Logger log = LogManager.getLogger(AuditEventTransformer.class);

    public static final String EVENT_CONTEXT_CATEGORY = "category";

    public static final String EVENT_CONTEXT_COMMENT = "comment";

    public static final String EVENT_CONTEXT_DELETED_DOCUMENT = "deletedDocument";

    public static final String EVENT_CONTEXT_EXTENDED_INFOS = "extendedInfos";

    protected ObjectMapper objectMapper;

    protected ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(new ExpressionFactoryImpl());

    public AuditEventTransformer() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(ExtendedInfo.class, new ExtendedInfoSerializer());
        objectMapper.registerModule(module);
    }

    @Override
    public boolean accept(Event event) {
        AuditLogger logger = Framework.getService(AuditLogger.class);
        return logger.getAuditableEventNames().contains(event.getName());
    }

    @Override
    public Map<String, String> buildEventRecordContext(Event event) {
        Map<String, String> context = new HashMap<>();
        EventContext eventContext = event.getContext();
        if (eventContext instanceof DocumentEventContext) {
            DocumentEventContext ctx = (DocumentEventContext) event.getContext();
            DocumentModel sourceDoc = ctx.getSourceDocument();
            if (sourceDoc != null) {
                putDocumentEventContext(context, sourceDoc);
            }
            context.put(EVENT_CONTEXT_REPOSITORY, ctx.getRepositoryName());

            ofNullable((Boolean) eventContext.getProperty(DISABLE_AUDIT_LOGGER)).ifPresent(
                    disableAuditLogger -> context.put(DISABLE_AUDIT_LOGGER, disableAuditLogger.toString()));

            ofNullable((String) eventContext.getProperty(TRANSTION_EVENT_OPTION_TO)).ifPresent(
                    to -> context.put(TRANSTION_EVENT_OPTION_TO, to));
        }

        ofNullable((String) eventContext.getProperty(EVENT_CONTEXT_CATEGORY)).ifPresent(
                category -> context.put(EVENT_CONTEXT_CATEGORY, category));

        ofNullable((String) eventContext.getProperty(EVENT_CONTEXT_COMMENT)).ifPresent(
                comment -> context.put(EVENT_CONTEXT_COMMENT, comment));

        try {
            putExtendedInfos(context, eventContext, event.getName());
        } catch (IOException e) {
            log.error("A error occurred while trying to get extended infos", e);
        }

        return context;
    }

    protected void putDocumentEventContext(Map<String, String> context, DocumentModel documentModel) {
        ofNullable(documentModel.getId()).ifPresent(id -> context.put(EVENT_CONTEXT_DOCUMENT_ID, id));
        ofNullable(documentModel.getPathAsString()).ifPresent(path -> context.put(EVENT_CONTEXT_DOCUMENT_PATH, path));
        context.put(EVENT_CONTEXT_DOCUMENT_TYPE, documentModel.getType());
        context.put(EVENT_CONTEXT_DOCUMENT_FACETS, String.join(",", documentModel.getFacets()));
        if (documentModel.isLifeCycleLoaded()) {
            context.put(EVENT_CONTEXT_DOCUMENT_LIFECYCLE_STATE, documentModel.getCurrentLifeCycleState());
        }
        context.put(EVENT_CONTEXT_DELETED_DOCUMENT, Boolean.toString(documentModel instanceof DeletedDocumentModel));
    }

    protected void putExtendedInfos(Map<String, String> contextMap, EventContext eventContext, String eventName)
            throws IOException {
        NXAuditEventsService auditService = (NXAuditEventsService) Framework.getRuntime()
                                                                            .getComponent(NXAuditEventsService.NAME);
        Set<ExtendedInfoDescriptor> descriptors = auditService.getExtendedInfoDescriptors();
        List<ExtendedInfoDescriptor> eventDescriptors = auditService.getEventExtendedInfoDescriptors().get(eventName);
        if (eventDescriptors != null && !eventDescriptors.isEmpty()) {
            descriptors.addAll(eventDescriptors);
        }
        ExpressionContext context = getExpressionContext(eventContext, auditService);

        Map<String, ExtendedInfo> extendedInfos = new HashMap<>();
        for (ExtendedInfoDescriptor desc : descriptors) {
            String exp = desc.getExpression();
            Serializable value;
            try {
                value = expressionEvaluator.evaluateExpression(context, exp, Serializable.class);
            } catch (PropertyException | UnsupportedOperationException | ELException e) {
                continue;
            } catch (DocumentNotFoundException e) {
                if (!DocumentEventTypes.DOCUMENT_REMOVED.equals(eventName)) {
                    log.error("Not found: {}", e.getMessage(), e);
                }
                continue;
            }
            if (value == null) {
                continue;
            }
            ExtendedInfo extendedInfo = auditService.getBackend().newExtendedInfo(value);
            extendedInfos.put(desc.getKey(), extendedInfo);
        }
        if (!extendedInfos.isEmpty()) {
            String extendedInfoValue = objectMapper.writeValueAsString(extendedInfos);
            contextMap.put(EVENT_CONTEXT_EXTENDED_INFOS, extendedInfoValue);
        }
    }

    protected ExpressionContext getExpressionContext(EventContext eventContext, NXAuditEventsService auditService) {
        ExpressionContext context = new ExpressionContext();
        if (eventContext != null) {
            expressionEvaluator.bindValue(context, "message", eventContext);
            if (eventContext instanceof DocumentEventContext) {
                DocumentModel source = ((DocumentEventContext) eventContext).getSourceDocument();
                expressionEvaluator.bindValue(context, "source", source);
                // inject now the adapters
                for (AdapterDescriptor ad : auditService.getDocumentAdapters()) {
                    if (source instanceof DeletedDocumentModel) {
                        continue; // skip
                    }
                    Object adapter = source.getAdapter(ad.getKlass());
                    if (adapter != null) {
                        expressionEvaluator.bindValue(context, ad.getName(), adapter);
                    }
                }
            }
            Principal principal = eventContext.getPrincipal();
            if (principal != null) {
                expressionEvaluator.bindValue(context, "principal", principal);
            }
        }
        return context;
    }

}
