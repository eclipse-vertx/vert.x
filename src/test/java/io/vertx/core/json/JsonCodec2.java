/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.json.JsonCodec;

import java.util.Random;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonCodec2 implements JsonCodec {

  public static final int ORDER = new Random().nextInt();

  @Override
  public int order() {
    return ORDER;
  }

  @Override
  public <T> T fromString(String json, Class<T> clazz) throws DecodeException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T fromBuffer(Buffer json, Class<T> clazz) throws DecodeException {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T fromValue(Object json, Class<T> toValueType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString(Object object, boolean pretty) throws EncodeException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Buffer toBuffer(Object object, boolean pretty) throws EncodeException {
    throw new UnsupportedOperationException();
  }
}
