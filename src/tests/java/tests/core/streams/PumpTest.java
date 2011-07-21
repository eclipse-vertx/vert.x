package tests.core.streams;

import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.streams.Pump;
import org.nodex.core.streams.ReadStream;
import org.nodex.core.streams.WriteStream;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tests.Utils;

/**
 * User: tfox
 * Date: 13/07/11
 * Time: 15:24
 */
public class PumpTest {

  @BeforeClass
  public void setUp() {
  }

  @AfterClass
  public void tearDown() {
  }

  @Test
  public void testPumpBasic() throws Exception {
    FakeReadStream rs = new FakeReadStream();
    FakeWriteStream ws = new FakeWriteStream();
    Pump p = new Pump(rs, ws, 1001);

    for (int i = 0; i < 10; i++) { // Repeat a few times
      p.start();

      Buffer inp = Buffer.newDynamic(0);
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
      Buffer inp = Buffer.newDynamic(0);
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
      inp = Buffer.newDynamic(0);
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

    public void data(DataHandler handler) {
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
  }

  private class FakeWriteStream implements WriteStream {

    int maxSize;
    Buffer received = Buffer.newDynamic(0);
    DoneHandler drainHandler;

    void clearReceived() {
      boolean callDrain = writeQueueFull();
      received = Buffer.newDynamic(0);
      if (callDrain && drainHandler != null) {
        drainHandler.onDone();
      }
    }

    public void setWriteQueueMaxSize(int maxSize) {
      this.maxSize = maxSize;
    }

    public boolean writeQueueFull() {
      return received.length() >= maxSize;
    }

    public void drain(DoneHandler handler) {
      this.drainHandler = handler;
    }

    public void write(Buffer data) {
      received.append(data);
    }

    public void write(String str) {
      received.append(Buffer.fromString(str));
    }

    public void write(String str, String enc) {
      received.append(Buffer.fromString(str, enc));
    }

    public void write(Buffer data, DoneHandler done) {
      received.append(data);
      done.onDone();
    }

    public void write(String str, DoneHandler done) {
      received.append(Buffer.fromString(str));
      done.onDone();
    }

    public void write(String str, String enc, DoneHandler done) {
      received.append(Buffer.fromString(str, enc));
      done.onDone();
    }
  }
}
