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

package org.nuxeo.ecm.core.event;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 11.1
 */
public class EventFeature implements RunnerFeature {

    @Override
    public void initialize(FeaturesRunner runner) {
        runner.getFeature(TransactionalFeature.class).addWaiter(duration -> {
            try {
                Framework.getService(EventService.class).waitForAsyncCompletion(duration.toMillis());
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        });
    }
}
