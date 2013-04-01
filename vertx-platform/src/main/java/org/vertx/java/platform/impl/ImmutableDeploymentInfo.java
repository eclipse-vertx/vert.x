package org.vertx.java.platform.impl;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.DeploymentInfo;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

/**
 * User: dsklyut
 * Date: 4/1/13
 * Time: 11:38 AM
 */
class ImmutableDeploymentInfo implements DeploymentInfo {

    private final Deployment deployment;
    private final Map<String, Deployment> deployments;


    ImmutableDeploymentInfo(Deployment deployment, Map<String, Deployment> holder) {
        this.deployment = deployment;
        this.deployments = holder;
    }

    @Override
    public String getName() {
        return deployment.name;
    }

    @Override
    public String getMain() {
        return deployment.main;
    }

    @Override
    public String getModId() {
        return deployment.modID.toString();
    }

    @Override
    public String getParentDeploymentName() {
        return deployment.parentDeploymentName;
    }

    @Override
    public String getRootDeploymentName() {
        Deployment dep = this.deployment;
        while (dep != null) {
            String parentDepName = dep.parentDeploymentName;
            if (parentDepName != null) {
                dep = deployments.get(parentDepName);
            } else {
                if (dep.modID != null) {
                    return dep.modID.toString();
                } else {
                    // Top level - deployed as verticle not module
                    // Just use the deployment name
                    return dep.name;
                }
            }
        }
        return null; // We are at the top level already
    }

    @Override
    public int getInstances() {
        return deployment.instances;
    }

    @Override
    public JsonObject getConfig() {
        return deployment.config.copy();
    }

    @Override
    public URL[] getUrls() {
        return Arrays.copyOf(deployment.urls, deployment.urls.length);
    }

    @Override
    public File getModDir() {
        return deployment.modDir;
    }

    @Override
    public boolean isAutoRedeploy() {
        return deployment.autoRedeploy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImmutableDeploymentInfo that = (ImmutableDeploymentInfo) o;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ImmutableDeploymentInfo");
        sb.append("{name='").append(getName()).append('\'');
        sb.append(", main='").append(getMain()).append('\'');
        sb.append(", modId='").append(getModId()).append('\'');
        sb.append(", parentDeploymentName='").append(getParentDeploymentName()).append('\'');
        sb.append(", rootDeploymentName='").append(getRootDeploymentName()).append('\'');
        sb.append(", instances=").append(getInstances());
        sb.append(", autoRedeploy=").append(isAutoRedeploy());
        sb.append(", config=").append(getConfig());
        sb.append('}');
        return sb.toString();
    }
}

