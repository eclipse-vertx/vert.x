package examples;

import io.vertx.core.AbstractVerticle;

/**
 * Just for the examples.
 */
public class ConfigurableVerticleExamples extends AbstractVerticle {


    @Override
    public void start() throws Exception {
        System.out.println("Configuration: " + config().getString("name"));
    }
}
