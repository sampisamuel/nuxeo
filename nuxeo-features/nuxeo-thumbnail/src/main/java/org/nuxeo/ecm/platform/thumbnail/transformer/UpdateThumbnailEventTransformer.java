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

package org.nuxeo.ecm.platform.thumbnail.transformer;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.DeletedDocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.async.EventTransformer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.thumbnail.ThumbnailConstants;

/**
 * @since 11.1
 */
public class UpdateThumbnailEventTransformer implements EventTransformer {

    @Override
    public boolean accept(Event event) {
        if (!ThumbnailConstants.EventNames.scheduleThumbnailUpdate.name().equals(event.getName())
                || !(event.getContext() instanceof DocumentEventContext)) {
            return false;
        }
        DocumentEventContext context = (DocumentEventContext) event.getContext();
        DocumentModel doc = context.getSourceDocument();
        return !(doc instanceof DeletedDocumentModel) && !doc.isProxy();
    }

    @Override
    public Map<String, String> buildEventRecordContext(Event event) {
        Map<String, String> context = new HashMap<>();
        EventContext eventContext = event.getContext();
        if (eventContext instanceof DocumentEventContext) {
            DocumentEventContext ctx = (DocumentEventContext) event.getContext();
            DocumentModel sourceDoc = ctx.getSourceDocument();
            context.put(EVENT_CONTEXT_DOCUMENT_ID, sourceDoc.getId());
            context.put(EVENT_CONTEXT_REPOSITORY, ctx.getRepositoryName());
        }

        return context;
    }

}
