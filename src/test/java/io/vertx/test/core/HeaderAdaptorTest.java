package io.vertx.test.core;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import org.junit.Test;

/**
 * Created by Bong on 2016-01-08.
 *
 * @author Bong
 * @version 1.0.0
 */
public class HeaderAdaptorTest extends VertxTestBase {
  private HttpClient client;

  public void setUp() throws Exception {
    super.setUp();
    client = vertx.createHttpClient(new HttpClientOptions());
  }

  protected void tearDown() throws Exception {
    client.close();
    super.tearDown();
  }

  @Test
  public void testRequestHeader() throws Exception {
    client.getAbs("http://www.google.com", response -> {
      final MultiMap headers = response.headers();
      assertNotNull(headers);
      testComplete();
    });
  }
}
