package org.vertx.java.core.app;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses args of the form -x y
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Args {

  public final Map<String, String> map = new HashMap<>();

  public Args(String[] args) {
    String currentKey = null;
    for (String arg: args) {
      if (arg.startsWith("-")) {
        if (currentKey != null) {
          map.put(currentKey, "");
          currentKey = null;
        }
        currentKey = arg;
      } else {
        if (currentKey != null) {
          map.put(currentKey, arg);
          currentKey = null;
        }
      }
    }
    if (currentKey != null) {
      map.put(currentKey, "");
    }
  }

  public int getPort() {
    String sport = map.get("-port");
    int port;
    if (sport != null) {
      try {
        port = Integer.parseInt(sport.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid port: " + sport);
      }
    } else {
      port = -1;
    }
    return port;
  }
}
