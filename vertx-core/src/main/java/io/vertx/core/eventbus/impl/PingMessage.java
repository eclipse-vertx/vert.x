/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.ServerID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PingMessage extends StringMessage {

  public PingMessage(ServerID sender) {
    super(true, "ping", "ping");
    this.sender = sender;
  }

  public PingMessage(Buffer readBuff) {
    super(readBuff);
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_PING;
  }

}
