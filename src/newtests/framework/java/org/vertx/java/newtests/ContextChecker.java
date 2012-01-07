package org.vertx.java.newtests;

import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextChecker {

  private static final Logger log = Logger.getLogger(ContextChecker.class);

  final Thread th;
  final Long contextID;
  final TestUtils tu;

  public ContextChecker(TestUtils tu) {
    this.th = Thread.currentThread();
    this.contextID = Vertx.instance.getContextID();
    if (contextID == null) {
      throw new IllegalStateException("Can't create checker on null context");
    }
    this.tu = tu;
  }

  public void check() {
    tu.azzert(th == Thread.currentThread(), "Expected:" + th + " Actual:" + Thread.currentThread());
    tu.azzert(contextID.equals(Vertx.instance.getContextID()), "Expected:" + contextID + " Actual:" + Vertx.instance
        .getContextID());
  }
}
