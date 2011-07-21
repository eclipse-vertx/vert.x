package org.nodex.tests.core;

import org.nodex.core.net.NetServer;
import org.nodex.tests.AwaitDone;

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
}
