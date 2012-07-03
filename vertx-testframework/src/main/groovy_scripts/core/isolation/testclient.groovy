package core.isolation

import java.util.concurrent.atomic.AtomicInteger
import org.vertx.groovy.framework.TestUtils

tu = new TestUtils(vertx)
tu.checkContext()

class CounterHolder {
  static final AtomicInteger counter = new AtomicInteger(0)
}

void testIsolation() {
  tu.azzert(CounterHolder.counter.incrementAndGet() == 1)
  tu.testComplete()
}

tu.registerTests(this)

tu.appReady()

void vertxStop() {
  tu.unregisterAll()
  tu.appStopped()
}

