package org.nodex.core.streams;

import org.nodex.core.DoneHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

/**
 * User: tfox
 * Date: 13/07/11
 * Time: 14:31
 */
public interface WriteStream {

  void data(DataHandler handler);

  void setWriteQueueMaxSize(int maxSize);

  boolean writeQueueFull();

  void drain(DoneHandler handler);

  void write(Buffer data);

  void write(String str);

  void write(String str, String enc);

  void write(Buffer data, final DoneHandler done);

  void write(String str, DoneHandler done);

  void write(String str, String enc, DoneHandler done);
}
