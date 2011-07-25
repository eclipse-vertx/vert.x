package org.nodex.core.streams;

import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

/**
 * User: tfox
 * Date: 13/07/11
 * Time: 14:35
 */
public class Pump {
  private final ReadStream readStream;
  private final WriteStream writeStream;

  private final DoneHandler drainHandler = new DoneHandler() {
    public void onDone() {
      readStream.resume();
    }
  };

  private final DataHandler dataHandler = new DataHandler() {
    public void onData(Buffer buffer) {
      writeStream.writeBuffer(buffer);
      if (writeStream.writeQueueFull()) {
        readStream.pause();
      }
    }
  };

  public Pump(ReadStream rs, WriteStream ws) {
    this.readStream = rs;
    this.writeStream = ws;
  }

  public Pump(ReadStream rs, WriteStream ws, int maxWriteQueueSize) {
    this(rs, ws);
    this.writeStream.setWriteQueueMaxSize(maxWriteQueueSize);
  }

  public void start() {
    writeStream.drain(drainHandler);
    readStream.data(dataHandler);
  }

  public void stop() {
    writeStream.drain(null);
    readStream.data(null);
  }

}
