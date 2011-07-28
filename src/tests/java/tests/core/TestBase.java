package tests.core;

import org.nodex.core.Nodex;
import org.nodex.core.http.HttpServer;
import org.nodex.core.net.NetServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import tests.AwaitDone;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * User: timfox
 * Date: 21/07/2011
 * Time: 13:26
 */
public class TestBase {

  /*
  We need to gather assertions and throw them at the end, this is because testng lets a test pass if an assertion
  is thrown from a different thread to that which ran the test
   */
  private static Queue<AssertionError> errors = new ConcurrentLinkedQueue<AssertionError>();

  public static void azzert(boolean exprValue) {
    azzert(exprValue, null);
  }

  public static void azzert(boolean exprValue, String msg) {
    if (!exprValue) {
      AssertionError err = new AssertionError(msg);
      err.fillInStackTrace();
      errors.add(err);
      throw err;
    }
  }

  protected void throwAssertions() {
    AssertionError err;
    while ((err = errors.poll()) != null) {
      throw err;
    }
  }

  protected void awaitClose(NetServer server) {
    AwaitDone await = new AwaitDone();
    server.close(await);
    azzert(await.awaitDone(5));
  }

  protected void awaitClose(HttpServer server) {
    AwaitDone await = new AwaitDone();
    server.close(await);
    azzert(await.awaitDone(5));
  }

  protected static class ContextChecker {
    final Thread th;
    final String contextID;

    public ContextChecker() {
      this.th = Thread.currentThread();
      this.contextID = Nodex.instance.getContextID();
    }

    public void check() {
      azzert(th == Thread.currentThread(), "Expected:" + th + " Actual:" + Thread.currentThread());
      azzert(contextID == Nodex.instance.getContextID(), "Expected:" + contextID + " Actual:" + Nodex.instance
          .getContextID());
    }
  }
}
