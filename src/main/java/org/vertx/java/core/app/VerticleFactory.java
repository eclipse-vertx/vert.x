package org.vertx.java.core.app;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface VerticleFactory {
  Verticle createVerticle(String main, ClassLoader parentCL) throws Exception;

}
