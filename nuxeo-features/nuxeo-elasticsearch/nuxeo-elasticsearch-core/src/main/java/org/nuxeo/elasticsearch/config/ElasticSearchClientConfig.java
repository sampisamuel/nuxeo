/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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

package org.nuxeo.elasticsearch.config;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.elasticsearch.api.ESClientFactory;

import java.io.Serializable;

/**
 * @since 9.1
 */
@XObject(value = "clientInitialization")
public class ElasticSearchClientConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @XNode("@class")
    protected Class<ESClientFactory> klass;

    @XNode("username")
    protected String username;

    @XNode("password")
    protected String password;

    @XNode("sslKeystorePath")
    protected String sslKeystorePath;

    @XNode("sslKeystorePassword")
    protected String sslKeystorePassword;

    public Class<ESClientFactory> getKlass() {
        return klass;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * @since 8.10-HF08, 9.2
     */
    public String getSslKeystorePath() {
        return sslKeystorePath;
    }

    /**
     * @since 8.10-HF08, 9.2
     */
    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

}