package tests.core.stdio;

import org.nodex.core.NodexMain;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.stdio.InStream;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User: tim
 * Date: 17/08/11
 * Time: 15:21
 */
public class StdioTest extends TestBase {

  //TODO need more tests!

  @Test
  public void testIn() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    new NodexMain() {
      public void go() throws Exception {
        String foo = Utils.randomAlphaString(1000);
        byte[] bytes = foo.getBytes("UTF-8");
        final Buffer buffin = Buffer.create(bytes);
        InputStream is = new ByteArrayInputStream(bytes);

        InStream in = new InStream(is);

        in.read(1000, new DataHandler() {
          public void onData(Buffer data) {
            azzert(Utils.buffersEqual(buffin, data));
            latch.countDown();
          }
        });
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }
}

