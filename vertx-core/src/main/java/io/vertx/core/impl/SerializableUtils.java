/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl;

import java.io.*;

public class SerializableUtils {

  public static byte[] toBytes(Object o) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(o);
      oos.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return baos.toByteArray();
  }

  @FunctionalInterface
  public interface ObjectInputStreamFactory {
    ObjectInputStream create(ByteArrayInputStream bais) throws IOException;
  }

  public static Object fromBytes(byte[] bytes, ObjectInputStreamFactory factory) {
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    try (ObjectInputStream ois = factory.create(bais)) {
      return ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private SerializableUtils() {
    // Utility class
  }
}
