package org.nodex.core.stdio;

import org.nodex.core.buffer.DataHandler;

import java.io.InputStream;

/**
 * User: tim
 * Date: 17/08/11
 * Time: 08:44
 */
class InStream {
  InStream(InputStream in) {
    this.in = in;
  }
  private final InputStream in;

  public void read(int bytes, DataHandler handler) {

  }
}
