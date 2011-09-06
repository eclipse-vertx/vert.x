package org.nodex.java.core.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: tim
 * Date: 06/09/11
 * Time: 10:33
 */
public class HeaderUtils {
  public static Map<String, String> simplifyHeaders(List<Map.Entry<String, String>> hdrs) {
    //According to http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2 we can legally convert any list of
    // HTTP headers into a map of headers where any header values
    //with the same key are concatenated together into a single value separated by commas
    //This hugely simplifies header handling
    Map<String, String> map = new HashMap<>();
    for (Map.Entry<String, String> entry : hdrs) {
      String prev = map.get(entry.getKey());
      if (prev != null) {
        StringBuilder sb = new StringBuilder(prev);
        sb.append(',').append(entry.getValue());
        map.put(entry.getKey(), sb.toString());
      } else {
        map.put(entry.getKey(), entry.getValue());
      }
    }
    return map;
  }
}
