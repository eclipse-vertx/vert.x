/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BufferExamples {

  public void createEmptyBuffer() {
    Buffer buff = Buffer.buffer();
  }

  public void createBufferFromString() {
    Buffer buff = Buffer.buffer("some-string");
  }

  public void createBufferFromEncodedString() {
    Buffer buff = Buffer.buffer("some-string", "UTF-16");
  }

  public void createWithInitialSize() {
    Buffer buff = Buffer.buffer(100000);
  }

  NetSocket socket;

  public void appendingToABuffer() throws Exception {
    Buffer buff = Buffer.buffer();

    buff.appendInt(123).appendString("hello\n");

    socket.write(buff);
  }

  public void randomAccessBufferWrite() throws Exception {
    Buffer buff = Buffer.buffer();

    buff.setInt(1000, 123);
    buff.setString(0, "hello");
  }

  private Buffer getSomeBuffer() {
    throw new UnsupportedOperationException();
  }

  public void readingFromABuffer() throws Exception {
    Buffer buff = getSomeBuffer();
    for (int i = 0; i < buff.length(); i += 4) {
      System.out.println("int value at " + i + " is " + buff.getInt(i));
    }
  }
}
