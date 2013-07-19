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

package vertx.tests.core.http;

import java.io.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSTestParams implements Serializable {
  public final boolean clientCert;
  public final boolean clientTrust;
  public final boolean serverCert;
  public final boolean serverTrust;
  public final boolean requireClientAuth;
  public final boolean clientTrustAll;
  public final boolean shouldPass;

  public TLSTestParams(boolean clientCert, boolean clientTrust, boolean serverCert, boolean serverTrust,
                       boolean requireClientAuth, boolean clientTrustAll, boolean shouldPass) {
    this.clientCert = clientCert;
    this.clientTrust = clientTrust;
    this.serverCert = serverCert;
    this.serverTrust = serverTrust;
    this.requireClientAuth = requireClientAuth;
    this.clientTrustAll = clientTrustAll;
    this.shouldPass = shouldPass;
  }

  public static TLSTestParams deserialize(byte[] serialized) {
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
      ObjectInputStream oos = new ObjectInputStream(bais);
      return (TLSTestParams)oos.readObject();
    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialise:" + e.getMessage());
    }
  }

  public byte[] serialize() {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(this);
      oos.flush();
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialise:" + e.getMessage());
    }
  }
}
