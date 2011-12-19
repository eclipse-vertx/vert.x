/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.tests.core.stdio;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.stdio.InStream;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StdioTest extends TestBase {

  //TODO need more org.vertx.tests!

  @Test
  public void testIn() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {
        String foo = Utils.randomAlphaString(1000);
        byte[] bytes;
        try {
          bytes = foo.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
          bytes = null;
        }
        final Buffer buffin = Buffer.create(bytes);
        InputStream is = new ByteArrayInputStream(bytes);

        InStream in = new InStream(is);

        final Buffer received = Buffer.create(0);

        in.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            received.appendBuffer(data);
          }
        });

        in.endHandler(new SimpleHandler() {
          public void handle() {
            azzert(Utils.buffersEqual(buffin, received));
            latch.countDown();
          }
        });

      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }

  @Test
  public void testOut() throws Exception {

    //TODO
  }
}

