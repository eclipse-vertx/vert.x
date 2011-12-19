package org.vertx.java.core.app;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface AppFactory {
  VertxApp createApp(String main, ClassLoader parentCL) throws Exception;

}
