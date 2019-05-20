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
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.schema;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * Descriptor representing a Nuxeo Property.
 * <p>
 * It maps the xml below:
 * <pre>
 * {@code <property schema="SCHEMA" name="NAME" secured="true" />}
 * </pre>
 *
 * @since 11.1
 */
@XObject("property")
public class PropertyDescriptor {

    @XNode("@schema")
    protected String schema;

    @XNode("@name")
    protected String name;

    @XNode("@secured")
    public Boolean secured;

    @XNode("@remove")
    public boolean remove;

    public String getId() {
        return schema + ':' + name;
    }

    public String getSchema() {
        return schema;
    }

    public String getName() {
        return name;
    }

    public boolean isSecured() {
        return Boolean.TRUE.equals(secured);
    }

    public PropertyDescriptor merge(PropertyDescriptor other) {
        PropertyDescriptor merged = new PropertyDescriptor();
        merged.schema = schema;
        merged.name = name;
        merged.secured = other.secured != null ? other.secured : secured;
        return merged;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
