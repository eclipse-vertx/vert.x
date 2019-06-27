/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.json;

import com.fasterxml.jackson.core.type.TypeReference;

public interface JsonMapper {

  /**
   * Encode a POJO to a Json data structure
   *
   * @param value
   * @return
   * @throws EncodeException If there was an error during encoding
   * @throws IllegalStateException If this mapper instance can't map the provided POJO
   */
  Object encode(Object value) throws EncodeException, IllegalStateException;

  /**
   * Decode a POJO from a Json data structure
   *
   * @param json
   * @param c
   * @return
   * @throws DecodeException If there was an error during decoding
   * @throws IllegalStateException If this mapper instance can't handle the provided class
   */
  <T> T decode(Object json, Class<T> c) throws DecodeException, IllegalStateException;

  <T> T decode(Object json, TypeReference<T> t) throws DecodeException, IllegalStateException;

}
