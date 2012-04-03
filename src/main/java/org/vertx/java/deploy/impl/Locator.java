package org.vertx.java.deploy.impl;

import org.vertx.java.core.Vertx;

/**
 * Used by the vert.x API in a scripting language to get references to the Java API
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Locator {
  public static Vertx vertx() {
    return Vertx.instance;
  }
}
