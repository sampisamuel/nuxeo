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
 *     bdelbosc
 */
package org.nuxeo.ecm.core.work;

import java.util.EnumSet;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.lib.stream.computation.RecordFilter;
import org.nuxeo.lib.stream.log.LogOffset;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;

/**
 * Filter that use a KVStore to pass big record value
 *
 * @since 11.1
 */
public class KVOverflowRecordFilter implements RecordFilter {
    protected static final Log log = LogFactory.getLog(KVOverflowRecordFilter.class);

    public static final String THRESHOLD_SIZE = "thresholdSize";

    public static final int DEFAULT_THRESHOLD_SIZE = 1_000_000;

    public static final String KV_STORE_OPTION = "keyValueStore";

    public static final String DEFAULT_KV_STORE = "default";

    public static final String PREFIX_OPTION = "prefix";

    public static final String DEFAULT_PREFIX = "bigValue:";

    int thresholdSize;

    String kvStoreName;

    String prefix;

    @Override
    public void init(Map<String, String> options) {
        String size = options.get(THRESHOLD_SIZE);
        if (size == null || size.isEmpty()) {
            thresholdSize = DEFAULT_THRESHOLD_SIZE;
        } else {
            thresholdSize = Integer.parseInt(size);
        }
        kvStoreName = options.getOrDefault(KV_STORE_OPTION, DEFAULT_KV_STORE);
        prefix = options.getOrDefault(PREFIX_OPTION, DEFAULT_PREFIX);
    }

    protected KeyValueStore getKeyValueStore() {
        return Framework.getService(KeyValueService.class).getKeyValueStore(kvStoreName);
    }

    @Override
    public Record beforeAppend(Record record) {
        if (record.data.length <= thresholdSize) {
            return record;
        }
        String key = getKVKey(record);
        if (log.isDebugEnabled()) {
            log.debug(String.format("Record: %s overflow value of size: %s into KV: %s", record.key, record.data.length,
                    key));
        }
        EnumSet<Record.Flag> flags = EnumSet.copyOf(record.flags);
        flags.add(Record.Flag.USER1);
        getKeyValueStore().put(key, record.data);
        return new Record(key, null, record.watermark, flags);
    }

    protected String getKVKey(Record record) {
        return prefix + record.key;
    }

    protected String getRecordKey(String key) {
        return key.replace(prefix, "");
    }

    @Override
    public Record afterRead(Record record, LogOffset offset) {
        if (record.flags.contains(Record.Flag.USER1) && (record.data == null || record.data.length == 0)) {
            log.debug("Read record value from KV: " + record.key);
            byte[] value = getKeyValueStore().get(record.key);
            if (value == null || value.length == 0) {
                log.error(
                        "Record value not found in KV Store for key: " + record.key + ", the record is lost, skipping");
                return null;
            }
            EnumSet<Record.Flag> flags = record.flags;
            flags.remove(Record.Flag.USER1);
            return new Record(getRecordKey(record.key), value, record.watermark, flags);
        }
        return record;
    }
}
