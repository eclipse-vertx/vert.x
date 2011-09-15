/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.core.stdio;

import org.nodex.java.core.Completion;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.Handler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.stdio.InStream;
import org.nodex.java.core.stdio.OutStream;
import org.nodex.java.core.streams.WriteStream;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class StdioTest extends TestBase {

  //TODO need more org.nodex.tests!

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

        final Buffer received = Buffer.create(0);

        in.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
           System.out.println("received data");
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
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }

  @Test
  public void testOut() throws Exception {

    //TODO
  }
}

