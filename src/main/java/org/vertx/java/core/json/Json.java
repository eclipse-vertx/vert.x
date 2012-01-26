package org.vertx.java.core.json;

import org.codehaus.jackson.map.ObjectMapper;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Json {

  final static ObjectMapper mapper = new ObjectMapper();

  static String encode(Object obj) throws EncodeException {
    try {
      return mapper.writeValueAsString(obj);
    }
    catch (Exception e) {
      throw new EncodeException("Failed to encode as JSON");
    }
  }
}
