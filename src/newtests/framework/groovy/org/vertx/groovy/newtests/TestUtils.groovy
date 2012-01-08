
package org.vertx.groovy.newtests

import org.vertx.java.core.Handler

class TestUtils extends org.vertx.java.newtests.TestUtils {

  // Provide a version of register which takes a closure
  def register(testName, handler) {
    super.register(testName, wrapHandler(handler))
  }

  private wrapHandler(hndlr) {
    return {hndlr.call()} as Handler
  }

}
