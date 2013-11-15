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
