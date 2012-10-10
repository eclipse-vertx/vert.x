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

package org.vertx.java.core.http.impl;

import org.vertx.java.core.impl.LowerCaseKeyMap;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HeaderUtils {
  public static Map<String, String> simplifyHeaders(List<Map.Entry<String, String>> hdrs) {
    // According to http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2 we can legally convert any list of
    // HTTP headers into a map of headers where any header values
    // with the same key are concatenated together into a single value separated by commas
    // This hugely simplifies header handling
    LowerCaseKeyMap<String> map = new LowerCaseKeyMap<>();
    for (Map.Entry<String, String> entry : hdrs) {
      // We lower case the key - HTTP header keys are case insensitive so
      // this is valid, and it makes it easier for the user since they don't
      // have to worry about whether the browser sent 'Content-Type' or 'content-type'
      // (for example)
      String hdrKey = entry.getKey().toLowerCase();
      String prev = map.getRaw(hdrKey);
      if (prev != null) {
        StringBuilder sb = new StringBuilder(prev);
        sb.append(',').append(entry.getValue());
        map.putRaw(hdrKey, sb.toString());
      } else {
        map.putRaw(hdrKey, entry.getValue());
      }
    }
    return map;
  }
}
