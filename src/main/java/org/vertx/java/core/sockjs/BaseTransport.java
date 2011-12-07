package org.vertx.java.core.sockjs;

import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class BaseTransport {

  protected final Map<String, Session> sessions;

  protected static final String COMMON_PATH_ELEMENT = "\\/([^\\/\\.]+)\\/([^\\/\\.]+)\\/";

  public BaseTransport(Map<String, Session> sessions) {
    this.sessions = sessions;
  }

  protected void setCookies(HttpServerRequest req) {
    String cookies = req.getHeader("Cookie");
    String jsessionID = "dummy";
    //Preserve existing JSESSIONID, if any
    if (cookies != null) {
      String[] parts;
      if (cookies.contains(";")) {
        parts = cookies.split(";");
      } else {
        parts = new String[] {cookies};
      }
      for (String part: parts) {
        if (part.startsWith("JSESSIONID")) {
          jsessionID = part.substring(11);
          break;
        }
      }
    }
    req.response.putHeader("Set-Cookie", "JSESSIONID=" + jsessionID + ";path=/");
  }

  protected void setCORS(HttpServerResponse resp, String origin) {
    resp.putHeader("Access-Control-Allow-Origin", origin);
    resp.putHeader("Access-Control-Allow-Credentials", "true");
  }
}
