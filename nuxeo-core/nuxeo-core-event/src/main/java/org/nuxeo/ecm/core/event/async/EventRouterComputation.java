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

import static org.nuxeo.runtime.stream.StreamServiceImpl.DEFAULT_CODEC;

import java.util.List;

import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.lib.stream.computation.AbstractBatchComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.codec.CodecService;

/**
 * Computation acting as a router for event records.
 *
 * @since 11.1
 */
public class EventRouterComputation extends AbstractBatchComputation {

    public static final String COMPUTATION_NAME = "EventRouter";

    protected List<EventRouter> eventRouters;

    public EventRouterComputation(String name, int nbInputStreams, int nbOutputStreams) {
        super(name, nbInputStreams, nbOutputStreams);
    }

    @Override
    public void init(ComputationContext context) {
        super.init(context);
        eventRouters = Framework.getService(EventService.class).getEventRouters();
    }

    @Override
    protected void batchProcess(ComputationContext context, String inputStreamName, List<Record> records) {
        for (Record record : records) {
            eventRouters.stream()
                        .filter(router -> router.accept(
                                Framework.getService(CodecService.class)
                                         .getCodec(DEFAULT_CODEC, EventRecord.class)
                                         .decode(record.getData())))
                        .forEach(router -> {
                            context.produceRecord(router.getStream(), record);
                            context.askForCheckpoint();
                        });
        }
    }

    @Override
    public void batchFailure(ComputationContext context, String inputStreamName, List<Record> records) {
        // TODO ?
    }

}
