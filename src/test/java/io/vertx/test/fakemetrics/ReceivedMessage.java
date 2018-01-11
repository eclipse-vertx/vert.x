/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
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
