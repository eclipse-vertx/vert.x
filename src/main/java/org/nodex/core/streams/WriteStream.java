package org.nodex.core.streams;

import org.nodex.core.buffer.Buffer;

/**
 * User: tfox
 * Date: 13/07/11
 * Time: 14:31
 */
public interface WriteStream {

  void setWriteQueueMaxSize(int maxSize);

  boolean writeQueueFull();

  void drain(Runnable handler);

  void writeBuffer(Buffer data);
}
