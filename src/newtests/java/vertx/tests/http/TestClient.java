package vertx.tests.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Immutable;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.java.newtests.TestUtils;
import org.vertx.java.tests.http.RequestInfo;
import org.vertx.java.tests.http.ResponseInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void test1() {
    //test("GET", "http://somehost:8080/some-uri", "/some-uri", null, null, null);
    request(new RequestInfo("GET", "http://localhost:8080/some-uri", "/some-uri", null, null, null, null),
            new ResponseInfo(200, "OK", null, null));
  }

  // RequestInfo(String method, String uri, String path, String query, Map<String, String> params, Map<String, String> headers, String body) {

  public void testWithParams() {
    Map<String, String> params = generateMap(10);
    String queryString = generateQueryString(params);
    request(new RequestInfo("GET", "http://localhost:8080/some-uri?" + queryString, "/some-uri", queryString, params, null, null),
            new ResponseInfo(200, "OK", null, null));
  }

  public void testWithHeaders() {
    Map<String, String> headers = generateMap(10);
    request(new RequestInfo("GET", "http://localhost:8080/some-uri", "/some-uri", null, null, headers, null),
            new ResponseInfo(200, "OK", null, null));
  }

  private String generateQueryString(Map<String, String> params) {
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (Map.Entry<String, String> param: params.entrySet()) {
      sb.append(param.getKey()).append("=").append(param.getValue());
      if (++count != params.size()) {
        sb.append("&");
      }
    }
    return sb.toString();
  }

  private Map<String, String> generateMap(int count) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < count; i++) {
      map.put(TestUtils.randomAlphaString(20), TestUtils.randomAlphaString(20));
    }
    return map;
  }

  public void request(RequestInfo requestInfo, final ResponseInfo respInfo) {
    HttpClient client = new HttpClient();
    client.setPort(8080).setHost("localhost");

    SharedData.<String, RequestInfo>getMap("params").put("requestinfo", requestInfo);
    SharedData.<String, ResponseInfo>getMap("params").put("responseinfo", respInfo);

    HttpClientRequest req = client.request(requestInfo.method, requestInfo.uri, new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse resp) {
        tu.azzert(resp.statusCode == respInfo.statusCode);
        tu.azzert(resp.statusMessage.equals(respInfo.statusMessage));
        if (respInfo.headers != null) {
          for (Map.Entry<String, String> header: respInfo.headers.entrySet()) {
            tu.azzert(resp.getHeaders().get(header.getKey()).equals(header.getValue()));
          }
        }
        tu.testComplete();
      }
    });

    if (requestInfo.headers != null) {
      req.putAllHeaders(requestInfo.headers);
    }

    if (requestInfo.body != null) {
      req.write(requestInfo.body);
    }

    req.end();

  }

}

