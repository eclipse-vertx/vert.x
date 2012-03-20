/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl.cli;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.deploy.impl.VerticleManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class VertxCommand implements Serializable {

  private static final Logger log = LoggerFactory.getLogger(VertxCommand.class);

  public abstract String execute(VerticleManager appMgr) throws Exception;

  public boolean isBlock() {
    return true;
  }

  public void write(NetSocket socket, Handler<Void> doneHandler) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(this);
      oos.flush();
      byte[] bytes = baos.toByteArray();
      Buffer buff = new Buffer(4 + bytes.length);
      buff.appendInt(bytes.length);
      buff.appendBytes(bytes);
      if (doneHandler == null) {
        socket.write(buff);
      } else {
        socket.write(buff, doneHandler);
      }
    } catch (Exception e) {
      log.error("Faile to write command", e);
    }
  }

  public static VertxCommand read(Buffer buff) throws Exception {
    byte[] bytes = buff.getBytes();
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    ObjectInputStream oois = new ObjectInputStream(bais);
    VertxCommand cmd = (VertxCommand)oois.readObject();
    return cmd;
  }
}
