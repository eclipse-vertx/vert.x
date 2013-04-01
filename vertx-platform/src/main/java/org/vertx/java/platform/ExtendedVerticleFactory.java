package org.vertx.java.platform;

/**
 * Version of VerticleFactory that can accept a bit more info vs. a plain main of the verticle/module
 * <p/>
 * User: dsklyut
 * Date: 4/1/13
 * Time: 11:32 AM
 */
public interface ExtendedVerticleFactory extends VerticleFactory {

    Verticle createVerticle(DeploymentInfo deploymentInfo) throws Exception;
}
