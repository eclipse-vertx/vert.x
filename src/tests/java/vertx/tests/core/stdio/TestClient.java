/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vertx.tests.core.stdio;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.stdio.InStream;
import org.vertx.java.framework.TestClientBase;
import org.vertx.java.framework.TestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;


/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testIn() {
    String foo = TestUtils.randomAlphaString(1000);
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
        tu.azzert(TestUtils.buffersEqual(buffin, received));
        tu.testComplete();
      }
    });
  }

  public void testOut() {
    //TODO
  }

}
