package org.vertx.java.deploy.impl;

import org.vertx.java.core.Vertx;
import org.vertx.java.deploy.Container;

/**
 * Used by the vert.x API in a scripting language to get references to the Java API
 * We create a distinct class instance in a different classloader for each
 * verticle so each one can see a different Vertx instance
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxLocator {
  public static Vertx vertx;
  public static Container container;
}
