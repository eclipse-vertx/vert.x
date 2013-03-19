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

package org.vertx.java.tests.core.streams;

import junit.framework.TestCase;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;
import org.vertx.java.testframework.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaPumpTest extends TestCase {

  @Test
  public void testPumpBasic() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = Pump.createPump(rs, ws, 1001);

    for (int i = 0; i < 10; i++) { // Repeat a few times
      p.start();

      Buffer inp = new Buffer();
      for (int j = 0; j < 10; j++) {
        Buffer b = TestUtils.generateRandomBuffer(100);
        inp.appendBuffer(b);
        rs.addData(b);
      }
      TestUtils.buffersEqual(inp, ws.received);
      assertFalse(rs.paused);
      assertEquals(0, rs.pauseCount);
      assertEquals(0, rs.resumeCount);

      p.stop();
      ws.clearReceived();
      Buffer b = TestUtils.generateRandomBuffer(100);
      rs.addData(b);
      assertEquals(0, ws.received.length());
    }
  }

  @Test
  public void testPumpPauseResume() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = Pump.createPump(rs, ws, 500);
    p.start();

    for (int i = 0; i < 10; i++) {   // Repeat a few times
      Buffer inp = new Buffer();
      for (int j = 0; j < 4; j++) {
        Buffer b = TestUtils.generateRandomBuffer(100);
        inp.appendBuffer(b);
        rs.addData(b);
        assertFalse(rs.paused);
        assertEquals(i, rs.pauseCount);
        assertEquals(i, rs.resumeCount);
      }
      Buffer b = TestUtils.generateRandomBuffer(100);
      inp.appendBuffer(b);
      rs.addData(b);
      assertTrue(rs.paused);
      assertEquals(i + 1, rs.pauseCount);
      assertEquals(i, rs.resumeCount);

      TestUtils.buffersEqual(inp, ws.received);
      ws.clearReceived();
      inp = new Buffer();
      assertFalse(rs.paused);
      assertEquals(i + 1, rs.pauseCount);
      assertEquals(i + 1, rs.resumeCount);
    }
  }

  private class FakeReadStream implements ReadStream {

    private Handler<Buffer> dataHandler;
    private boolean paused;
    int pauseCount;
    int resumeCount;

    void addData(Buffer data) {
      if (dataHandler != null) {
        dataHandler.handle(data);
      }
    }

    public void dataHandler(Handler<Buffer> handler) {
      this.dataHandler = handler;
    }

    public void pause() {
      paused = true;
      pauseCount++;
    }

    public void resume() {
      paused = false;
      resumeCount++;
    }

    public void exceptionHandler(Handler<Exception> handler) {
    }

    public void endHandler(Handler<Void> endHandler) {
    }
  }

  private class FakeWriteStream implements WriteStream {

    int maxSize;
    Buffer received = new Buffer();
    Handler<Void> drainHandler;

    void clearReceived() {
      boolean callDrain = writeQueueFull();
      received = new Buffer();
      if (callDrain && drainHandler != null) {
        drainHandler.handle(null);
      }
    }

    public void setWriteQueueMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public boolean writeQueueFull() {
      return received.length() >= maxSize;
    }

    public void drainHandler(Handler<Void> handler) {
      this.drainHandler = handler;
    }

    public void writeBuffer(Buffer data) {
      received.appendBuffer(data);
    }

    public void exceptionHandler(Handler<Exception> handler) {
    }
  }
}
