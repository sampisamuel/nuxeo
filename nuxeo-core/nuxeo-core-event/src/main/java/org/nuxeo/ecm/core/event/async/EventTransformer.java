/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Contributors:
 *      Funsho David
 */
package org.nuxeo.ecm.core.event.async;

import java.util.Map;

import org.nuxeo.ecm.core.event.Event;

/**
 * The EventTransformer takes an event to extract context information from the event in order to add it to the
 * EventRecord created by {@link EventsStreamListener}.
 *
 * @since 11.1
 */
public interface EventTransformer {

    String EVENT_CONTEXT_DOCUMENT_ID = "documentId";

    String EVENT_CONTEXT_DOCUMENT_PATH = "documentPath";

    String EVENT_CONTEXT_DOCUMENT_TYPE = "documentType";

    String EVENT_CONTEXT_DOCUMENT_FACETS = "documentFacets";

    String EVENT_CONTEXT_DOCUMENT_LIFECYCLE_STATE = "documentLifecycleState";

    String EVENT_CONTEXT_REPOSITORY = "repository";

    boolean accept(Event event);

    Map<String, String> buildEventRecordContext(Event event);

}
