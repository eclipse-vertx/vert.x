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

package io.vertx.test.fakemetrics;

/**
* @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
*/
public class ReceivedMessage {

  public final String address;
  public final boolean publish;
  public final boolean local;
  public final int handlers;

  public ReceivedMessage(String address, boolean publish, boolean local, int handlers) {
    this.address = address;
    this.publish = publish;
    this.local = local;
    this.handlers = handlers;
  }

  @Override
  public boolean equals(Object obj) {
    ReceivedMessage that = (ReceivedMessage) obj;
    return address.equals(that.address) && publish == that.publish && local == that.local && handlers == that.handlers;
  }

  @Override
  public String toString() {
    return "Message[address" + address + ",publish=" + publish + ",local=" + local + ",handlers=" + handlers + "]";
  }
}
