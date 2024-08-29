package io.vertx.core.http.headers;

import io.netty.handler.codec.Headers;
import io.vertx.core.http.impl.headers.VertxHttpHeadersBase;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public abstract class VertxHttpHeadersTestBase<H extends Headers<CharSequence, CharSequence, H>> extends HeadersTestBase {
  private VertxHttpHeadersBase<H> vertxHttpHeaders;

  protected abstract VertxHttpHeadersBase<H> newVertxHttpHeaders();

  @Before
  public void doBefore() {
    vertxHttpHeaders = newVertxHttpHeaders();
  }

  @Test
  public void testMethod() {
    vertxHttpHeaders.method("GET");
    assertEquals("GET", vertxHttpHeaders.method());
  }

  @Test
  public void testAuthority() {
    vertxHttpHeaders.authority("Auth");
    assertEquals("Auth", vertxHttpHeaders.authority());
  }

  @Test
  public void testPath() {
    vertxHttpHeaders.path("Path");
    assertEquals("Path", vertxHttpHeaders.path());
  }

  @Test
  public void testScheme() {
    vertxHttpHeaders.scheme("https");
    assertEquals("https", vertxHttpHeaders.scheme());
  }

  @Test
  public void testStatus() {
    vertxHttpHeaders.status("100");
    assertEquals("100", vertxHttpHeaders.status());
  }

  @Test
  public void testToHeaderAdapter() {
    //TODO: impl
  }

  @Test
  public void testToHttpHeaders() {
    //TODO: impl
  }
}
