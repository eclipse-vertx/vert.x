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

package org.vertx.java.core.json.impl;

import org.codehaus.jackson.map.ObjectMapper;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.EncodeException;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Json {

  private static final Logger log = LoggerFactory.getLogger(Json.class);

  private final static ObjectMapper mapper = new ObjectMapper();

  public static String encode(Object obj) throws EncodeException {
    try {
      return mapper.writeValueAsString(obj);
    }
    catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
    }
  }

  public static Object decodeValue(String str, Class clazz) throws DecodeException {
    try {
      return mapper.readValue(str, clazz);
    }
    catch (Exception e) {
      throw new DecodeException("Failed to decode:" + e.getMessage());
    }
  }

}
