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

package org.vertx.java.core.eventbus.impl.hazelcast;

import com.hazelcast.nio.DataSerializable;
import org.vertx.java.core.net.impl.ServerID;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HazelcastServerID implements DataSerializable {

  public ServerID serverID;

  public HazelcastServerID() {
  }

  public HazelcastServerID(ServerID serverID) {
    this.serverID = serverID;
  }

  @Override
  public void writeData(DataOutput dataOutput) throws IOException {
    dataOutput.writeInt(serverID.port);
    dataOutput.writeUTF(serverID.host);
  }

  @Override
  public void readData(DataInput dataInput) throws IOException {
    int port = dataInput.readInt();
    String host = dataInput.readUTF();
    serverID = new ServerID(port, host);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HazelcastServerID that = (HazelcastServerID) o;
    return serverID.equals(that.serverID);
  }

  @Override
  public int hashCode() {
    return serverID.hashCode();
  }
}
