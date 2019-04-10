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

package org.nuxeo.ecm.platform.thumbnail.router;

import org.nuxeo.ecm.core.event.async.AbstractEventRouter;
import org.nuxeo.ecm.core.event.async.EventRecord;
import org.nuxeo.ecm.platform.thumbnail.ThumbnailConstants;

/**
 * @since 11.1
 */
public class UpdateThumbnailEventRouter extends AbstractEventRouter {

    public UpdateThumbnailEventRouter(String id, String stream) {
        super(id, stream);
    }

    @Override
    public boolean accept(EventRecord eventRecord) {
        return ThumbnailConstants.EventNames.scheduleThumbnailUpdate.name().equals(eventRecord.getName());
    }
}
