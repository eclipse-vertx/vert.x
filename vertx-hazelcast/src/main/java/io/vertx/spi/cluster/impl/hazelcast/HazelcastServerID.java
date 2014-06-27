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

package io.vertx.spi.cluster.impl.hazelcast;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import io.vertx.core.net.impl.ServerID;

import java.io.IOException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class HazelcastServerID extends ServerID implements DataSerializable {

  public HazelcastServerID() {
  }

  public HazelcastServerID(ServerID serverID) {
    super(serverID.port, serverID.host);
  }

  @Override
  public void writeData(ObjectDataOutput dataOutput) throws IOException {
    dataOutput.writeInt(port);
    dataOutput.writeUTF(host);
  }

  @Override
  public void readData(ObjectDataInput dataInput) throws IOException {
    port = dataInput.readInt();
    host = dataInput.readUTF();
  }

  // We replace any ServerID instances with HazelcastServerID - this allows them to be serialized more optimally using
  // DataSerializable
  public static <V> V convertServerID(V val) {
    if (val.getClass() == ServerID.class) {
      ServerID sid = (ServerID)val;
      HazelcastServerID hsid = new HazelcastServerID(sid);
      return (V)hsid;
    } else {
      return val;
    }
  }

}
