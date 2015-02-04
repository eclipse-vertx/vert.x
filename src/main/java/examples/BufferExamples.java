/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.MessageConsumer;
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



}
