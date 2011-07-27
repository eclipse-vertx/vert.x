package tests.core;

import org.nodex.core.Nodex;
import org.nodex.core.http.HttpServer;
import org.nodex.core.net.NetServer;
import tests.AwaitDone;


/**
 * User: timfox
 * Date: 21/07/2011
 * Time: 13:26
 */
public class TestBase {
  protected void awaitClose(NetServer server) {
    AwaitDone await = new AwaitDone();
    server.close(await);
    assert await.awaitDone(5);
  }

  protected void awaitClose(HttpServer server) {
    AwaitDone await = new AwaitDone();
    server.close(await);
    assert await.awaitDone(5);
  }

  protected static class ContextChecker {
    final Thread th;
    final String contextID;

    public ContextChecker() {
      this.th = Thread.currentThread();
      this.contextID = Nodex.instance.getContextID();
    }

    public void check() {
      assert th == Thread.currentThread() : "Expected:" + th + " Actual:" + Thread.currentThread();
      assert contextID == Nodex.instance.getContextID() : "Expected:" + contextID + " Actual:" + Nodex.instance
          .getContextID();
    }
  }
}
