package org.nodex.core.streams;

import org.nodex.core.buffer.DataHandler;

/**
 * User: tfox
 * Date: 13/07/11
 * Time: 14:30
 */
public interface ReadStream {

  void data(DataHandler handler);

  void pause();

  void resume();
}
