/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package examples;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

/**
 * Created by tim on 09/01/15.
 */
public class BufferExamples {

  public void example1() {
    Buffer buff = Buffer.buffer();
  }

  public void example2() {
    Buffer buff = Buffer.buffer("some string");
  }

  public void example3() {
    Buffer buff = Buffer.buffer("some string", "UTF-16");
  }

  public void example5() {
    Buffer buff = Buffer.buffer(10000);
  }

  public void example6(NetSocket socket) {
    Buffer buff = Buffer.buffer();

    buff.appendInt(123).appendString("hello\n");

    socket.write(buff);
  }

  public void example7() {
    Buffer buff = Buffer.buffer();

    buff.setInt(1000, 123);
    buff.setString(0, "hello");
  }

  public void example8() {
    Buffer buff = Buffer.buffer();
    for (int i = 0; i < buff.length(); i += 4) {
      System.out.println("int value at " + i + " is " + buff.getInt(i));
    }
  }

  public void example9() {
    Buffer buff = Buffer.buffer(128);
    int pos = 15;
    buff.setUnsignedByte(pos, (short) 200);
    System.out.println(buff.getUnsignedByte(pos));
  }

}
