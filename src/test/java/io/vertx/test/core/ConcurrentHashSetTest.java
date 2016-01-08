package io.vertx.test.core;

import io.vertx.core.impl.ConcurrentHashSet;
import org.junit.Test;

import java.util.Set;

import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.assertNotNull;

/**
 * Created by Bong on 2016-01-08.
 *
 * @author Bong
 * @version 1.0.0
 */
public class ConcurrentHashSetTest {
  @Test
  public void testToString() throws Exception {
    final Set<String> concurrentSet = new ConcurrentHashSet<>();
    concurrentSet.add("apple");
    concurrentSet.add("basement");
    concurrentSet.add("cone");
    concurrentSet.add("door");
    concurrentSet.add("elephant");
    concurrentSet.add("apple");
    assertNotNull(concurrentSet.toString());
    assertNullPointerException(concurrentSet::toString);
  }
}
