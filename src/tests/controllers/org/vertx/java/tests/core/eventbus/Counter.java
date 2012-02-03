package org.vertx.java.tests.core.eventbus;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Counter {
  // We use this to get a unique port id
  public static AtomicInteger portCounter = new AtomicInteger(25500);

}
