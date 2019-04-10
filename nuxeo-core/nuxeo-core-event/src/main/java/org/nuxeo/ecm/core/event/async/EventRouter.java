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

package org.nuxeo.ecm.core.event.async;

/**
 * Event router interface. An event router dispatch event records to the consumers that are interested in it.
 * 
 * @since 11.1
 */
public interface EventRouter {

    /**
     * Accepts if an event record should be routed to the output category stream.
     */
    boolean accept(EventRecord eventRecord);

    /**
     * Gets router id.
     */
    String getId();

    /**
     * Gets stream where the event record will be redirected.
     */
    String getStream();

}
