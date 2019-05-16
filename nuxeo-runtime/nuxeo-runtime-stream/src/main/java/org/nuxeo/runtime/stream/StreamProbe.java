package org.nuxeo.runtime.stream;

/*
 * (C) Copyright 2019 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     pierre
 */
import java.util.Date;

import org.nuxeo.common.utils.DateUtils;
import org.nuxeo.lib.stream.computation.log.ComputationRunnerTerminated;
import org.nuxeo.lib.stream.computation.log.ComputationRunnerTerminated.ComputationRunnerTerminatedContext;
import org.nuxeo.runtime.management.api.Probe;
import org.nuxeo.runtime.management.api.ProbeStatus;

public class StreamProbe implements Probe {

    protected static final String MESSAGE = "ComputationRunner '%s' responsible for partitions %s is blocked since %s after %d retries";

    @Override
    public ProbeStatus run() {
        if (ComputationRunnerTerminated.hasBlockedStream()) {
            StringBuilder message = new StringBuilder();
            for (ComputationRunnerTerminatedContext context : ComputationRunnerTerminated.getErrors()) {
                String line = String.format(MESSAGE, //
                        context.name, //
                        context.partitions.toString(), //
                        DateUtils.formatISODateTime(new Date(context.timestamp)), //
                        context.retryPolicy.getMaxRetries());
                message.append(line).append("\n");
            }
            return ProbeStatus.newFailure(message.toString());
        }
        return ProbeStatus.newSuccess("Stream are running fine");
    }
}
