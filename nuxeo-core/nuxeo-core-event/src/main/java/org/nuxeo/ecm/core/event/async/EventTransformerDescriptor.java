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

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.model.Descriptor;

/**
 * Descriptor for the contribution of an EventTransformer.
 *
 * @since 11.1
 */
@XObject("eventTransformer")
public class EventTransformerDescriptor implements Descriptor {

    @XNode("@id")
    protected String id;

    @XNode("@class")
    protected Class<? extends EventTransformer> eventTransformerClass;

    @Override
    public String getId() {
        return id;
    }

    public EventTransformer newInstance() {
        try {
            return eventTransformerClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException(e);
        }
    }
}
