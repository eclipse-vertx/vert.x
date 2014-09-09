/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PumpTest {

  @Test
  public void testPumpBasic() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = Pump.pump(rs, ws, 1001);

    for (int i = 0; i < 10; i++) { // Repeat a few times
      p.start();

      Buffer inp = Buffer.buffer();
      for (int j = 0; j < 10; j++) {
        Buffer b = TestUtils.randomBuffer(100);
        inp.appendBuffer(b);
        rs.addData(b);
      }
      assertEquals(inp, ws.received);
      assertFalse(rs.paused);
      assertEquals(0, rs.pauseCount);
      assertEquals(0, rs.resumeCount);

      p.stop();
      ws.clearReceived();
      Buffer b = TestUtils.randomBuffer(100);
      rs.addData(b);
      assertEquals(0, ws.received.length());
    }
  }

  @Test
  public void testPumpPauseResume() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = Pump.pump(rs, ws, 500);
    p.start();

    for (int i = 0; i < 10; i++) {   // Repeat a few times
      Buffer inp = Buffer.buffer();
      for (int j = 0; j < 4; j++) {
        Buffer b = TestUtils.randomBuffer(100);
        inp.appendBuffer(b);
        rs.addData(b);
        assertFalse(rs.paused);
        assertEquals(i, rs.pauseCount);
        assertEquals(i, rs.resumeCount);
      }
      Buffer b = TestUtils.randomBuffer(100);
      inp.appendBuffer(b);
      rs.addData(b);
      assertTrue(rs.paused);
      assertEquals(i + 1, rs.pauseCount);
      assertEquals(i, rs.resumeCount);

      assertEquals(inp, ws.received);
      ws.clearReceived();
      inp = Buffer.buffer();
      assertFalse(rs.paused);
      assertEquals(i + 1, rs.pauseCount);
      assertEquals(i + 1, rs.resumeCount);
    }
  }

  private class FakeReadStream implements ReadStream<FakeReadStream> {

    private Handler<Buffer> dataHandler;
    private boolean paused;
    int pauseCount;
    int resumeCount;

    void addData(Buffer data) {
      if (dataHandler != null) {
        dataHandler.handle(data);
      }
    }

    public FakeReadStream dataHandler(Handler<Buffer> handler) {
      this.dataHandler = handler;
      return this;
    }

    public FakeReadStream pause() {
      paused = true;
      pauseCount++;
      return this;
    }

    public FakeReadStream resume() {
      paused = false;
      resumeCount++;
      return this;
    }

    public FakeReadStream exceptionHandler(Handler<Throwable> handler) {
      return this;
    }

    public FakeReadStream endHandler(Handler<Void> endHandler) {
      return this;
    }
  }

  private class FakeWriteStream implements WriteStream<FakeWriteStream> {

    int maxSize;
    Buffer received = Buffer.buffer();
    Handler<Void> drainHandler;

    void clearReceived() {
      boolean callDrain = writeQueueFull();
      received = Buffer.buffer();
      if (callDrain && drainHandler != null) {
        drainHandler.handle(null);
      }
    }

    public FakeWriteStream setWriteQueueMaxSize(int maxSize) {
      this.maxSize = maxSize;
      return this;
    }

    public boolean writeQueueFull() {
      return received.length() >= maxSize;
    }

    public FakeWriteStream drainHandler(Handler<Void> handler) {
      this.drainHandler = handler;
      return this;
    }

    public FakeWriteStream write(Buffer data) {
      received.appendBuffer(data);
      return this;
    }

    public FakeWriteStream exceptionHandler(Handler<Throwable> handler) {
      return this;
    }
  }
}
