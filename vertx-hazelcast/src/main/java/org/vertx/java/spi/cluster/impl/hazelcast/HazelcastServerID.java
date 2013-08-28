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

package org.vertx.java.spi.cluster.impl.hazelcast;

import com.hazelcast.nio.DataSerializable;
import org.vertx.java.core.net.impl.ServerID;

import java.io.DataInput;
import java.io.DataOutput;
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
  public void writeData(DataOutput dataOutput) throws IOException {
    dataOutput.writeInt(port);
    dataOutput.writeUTF(host);
  }

  @Override
  public void readData(DataInput dataInput) throws IOException {
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
