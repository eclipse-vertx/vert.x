package org.vertx.java.newtests;

import org.vertx.java.core.Vertx;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextChecker {
  final Thread th;
  final long contextID;
  final TestUtils tu;

  public ContextChecker(TestUtils tu) {
    this.th = Thread.currentThread();
    this.contextID = Vertx.instance.getContextID();
    this.tu = tu;
  }

  public void check() {
    tu.azzert(th == Thread.currentThread(), "Expected:" + th + " Actual:" + Thread.currentThread());
    tu.azzert(contextID == Vertx.instance.getContextID(), "Expected:" + contextID + " Actual:" + Vertx.instance
        .getContextID());
  }
}
