/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tests.core.streams;

import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.Pump;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;
import org.testng.annotations.Test;
import tests.Utils;

public class PumpTest {

  @Test
  public void testPumpBasic() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = new Pump(rs, ws, 1001);

    for (int i = 0; i < 10; i++) { // Repeat a few times
      p.start();

      Buffer inp = Buffer.createBuffer(0);
      for (int j = 0; j < 10; j++) {
        Buffer b = Utils.generateRandomBuffer(100);
        inp.append(b);
        rs.addData(b);
      }
      Utils.buffersEqual(inp, ws.received);
      assert !rs.paused;
      assert rs.pauseCount == 0;
      assert rs.resumeCount == 0;

      p.stop();
      ws.clearReceived();
      Buffer b = Utils.generateRandomBuffer(100);
      rs.addData(b);
      assert ws.received.length() == 0;
    }
  }

  @Test
  public void testPumpPauseResume() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = new Pump(rs, ws, 500);
    p.start();

    for (int i = 0; i < 10; i++) {   // Repeat a few times
      Buffer inp = Buffer.createBuffer(0);
      for (int j = 0; j < 4; j++) {
        Buffer b = Utils.generateRandomBuffer(100);
        inp.append(b);
        rs.addData(b);
        assert !rs.paused;
        assert rs.pauseCount == i;
        assert rs.resumeCount == i;
      }
      Buffer b = Utils.generateRandomBuffer(100);
      inp.append(b);
      rs.addData(b);
      assert rs.paused;
      assert rs.pauseCount == i + 1;
      assert rs.resumeCount == i;

      Utils.buffersEqual(inp, ws.received);
      ws.clearReceived();
      inp = Buffer.createBuffer(0);
      assert !rs.paused;
      assert rs.pauseCount == i + 1;
      assert rs.resumeCount == i + 1;
    }
  }

  private class FakeReadStream implements ReadStream {

    private DataHandler dataHandler;
    private boolean paused;
    int pauseCount;
    int resumeCount;

    void addData(Buffer data) {
      if (dataHandler != null) {
        dataHandler.onData(data);
      }
    }

    public void dataHandler(DataHandler handler) {
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

    public void exceptionHandler(ExceptionHandler handler) {
    }

    public void endHandler(Runnable endHandler) {
    }
  }

  private class FakeWriteStream implements WriteStream {

    int maxSize;
    Buffer received = Buffer.createBuffer(0);
    Runnable drainHandler;

    void clearReceived() {
      boolean callDrain = writeQueueFull();
      received = Buffer.createBuffer(0);
      if (callDrain && drainHandler != null) {
        drainHandler.run();
      }
    }

    public void setWriteQueueMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public boolean writeQueueFull() {
      return received.length() >= maxSize;
    }

    public void drainHandler(Runnable handler) {
      this.drainHandler = handler;
    }

    public void writeBuffer(Buffer data) {
      received.append(data);
    }

    public void exceptionHandler(ExceptionHandler handler) {
    }
  }
}
