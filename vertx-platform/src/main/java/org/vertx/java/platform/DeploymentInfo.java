package org.vertx.java.platform;

import org.vertx.java.core.json.JsonObject;

import java.io.File;
import java.net.URL;

/**
 * Immutable representation of the current deployment
 * <p/>
 * User: dsklyut
 * Date: 4/1/13
 * Time: 11:33 AM
 */
public interface DeploymentInfo {

    /**
     * Name of deployment
     */
    String getName();

    /**
     * Main of deployment
     */
    String getMain();

    /**
     * ModId, if mod, null otherwise?
     */
    String getModId();

    /**
     * parent deployment module, if there is one, null otherwise (i.e. runVerticle vs. runMod)
     */
    String getParentDeploymentName();

    /**
     * Root deployment name - mod a -> mod b -> verticle x -> worker c.
     * a is root.
     */
    String getRootDeploymentName();

    /**
     * Number of instances in this deployment
     */
    int getInstances();

    /**
     * deployment config
     */
    JsonObject getConfig();

    /**
     * urls of the deployment.
     */
    URL[] getUrls();

    /**
     * mod home directory
     */
    File getModDir();

    /**
     * is this eligible for auto re-deploy
     */
    boolean isAutoRedeploy();

    /**
     * need an equality guarantee in case of instances > 0 and concurrent deployments
     *
     * @param o
     */
    boolean equals(Object o);

    /**
     * hashCode for symmetry with equals
     */
    int hashCode();

}
