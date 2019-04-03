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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.avro.reflect.Nullable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


/**
 * A record representing an Event.
 *
 * @since 11.1
 */
public class EventRecord {

    protected EventRecord() {
        // Empty constructor for Avro decoder
    }

    protected String id;

    protected String name;

    @Nullable
    protected String username;

    protected long time;

    protected Map<String, String> context = new HashMap<>();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public long getTime() {
        return time;
    }

    public Map<String, String> getContext() {
        return Collections.unmodifiableMap(context);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Event record builder class */
    public static class Builder {

        protected EventRecord record;

        protected Builder() {
            record = new EventRecord();
        }

        public Builder name(String eventName) {
            record.name = eventName;
            return this;
        }

        public Builder username(String username) {
            record.username = username;
            return this;
        }

        public Builder time(long time) {
            record.time = time;
            return this;
        }

        public Builder context(Map<String, String> context) {
            record.context.putAll(context);
            return this;
        }

        public EventRecord build() {
            record.id = UUID.randomUUID().toString();
            return record;
        }
    }
}
