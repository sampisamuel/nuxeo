/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.admin.runtime;

import java.util.List;

/**
 * Holds information about current deployed Nuxeo Platform
 *
 * @author tiry
 */
public class SimplifiedServerInfo {

    protected List<SimplifiedBundleInfo> bundleInfos;

    protected String runtimeVersion;

    protected List<String> warnings;

    protected List<String> errors;

    public List<SimplifiedBundleInfo> getBundleInfos() {
        return bundleInfos;
    }

    public void setBundleInfos(List<SimplifiedBundleInfo> bundleInfos) {
        this.bundleInfos = bundleInfos;
    }

    public String getApplicationName() {
        return PlatformVersionHelper.getApplicationName();
    }

    public String getApplicationVersion() {
        return PlatformVersionHelper.getApplicationVersion();
    }

    public String getDistributionName() {
        return PlatformVersionHelper.getDistributionName();
    }

    public String getDistributionVersion() {
        return PlatformVersionHelper.getDistributionVersion();
    }

    public String getDistributionHost() {
        return PlatformVersionHelper.getDistributionHost();
    }

    public String getDistributionDate() {
        return PlatformVersionHelper.getDistributionDate();
    }

    public String getRuntimeVersion() {
        return runtimeVersion;
    }

    public void setRuntimeVersion(String runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * @since 9.1
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * @since 9.1
     */
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    /**
     * @since 9.1
     */
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getApplicationName());
        sb.append("  ");
        sb.append(getApplicationVersion());
        sb.append("\n");

        sb.append("runtime :  ");
        sb.append(runtimeVersion);
        sb.append("\n");

        sb.append("warnings :  ");
        if (warnings == null || warnings.isEmpty()) {
            sb.append("none");
        } else {
            for (String warn : warnings) {
                sb.append("\n  ");
                sb.append(warn);
            }
        }

        sb.append("errors :  ");
        if (errors == null || errors.isEmpty()) {
            sb.append("none");
        } else {
            for (String error : errors) {
                sb.append("\n  ");
                sb.append(error);
            }
        }

        sb.append("\nbundles :  ");
        for (SimplifiedBundleInfo bi : bundleInfos) {
            sb.append("\n  ");
            sb.append(bi.getName());
            sb.append("    (");
            sb.append(bi.getVersion());
            sb.append(")");
        }

        return sb.toString();
    }
}
