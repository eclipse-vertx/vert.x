package io.vertx.tests.http;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpResponseHead;
import io.vertx.core.http.impl.HttpServerConnection;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.HttpBodyDecoder;
import io.vertx.test.core.TestUtils;
import io.vertx.test.http.HttpConfigurator;
import io.vertx.test.http.SimpleHttpTest2;
import org.junit.Test;

import static org.junit.Assert.fail;

public class HttpBodyCodecTest extends SimpleHttpTest2 {

  public HttpBodyCodecTest() {
    super(HttpConfigurator.H2.MULTIPLEX);
  }

  @Test
  public void testBodyDecoder() throws Exception {
    Buffer body = Buffer.buffer(TestUtils.randomAlphaString(1024));
    server.connectionHandler(connection -> {
      HttpServerConnection serverConnection = (HttpServerConnection) connection;
      serverConnection.streamHandler(stream -> {
        stream.bodyDecoder(new HttpBodyDecoder() {
          @Override
          public int handle(ByteBuf content) {
            ReferenceCountUtil.release(content);
            return 10;
          }
          @Override
          public void next() {
          }
          @Override
          public void end() {
            stream.writeHead(new HttpResponseHead(200, null, new HttpResponseHeaders(serverConnection.newHeaders())), null, true);
          }
        });
      });
    });
    server.requestHandler(request -> {
      fail();
    });
    startServer(testAddress);
    client.request(new RequestOptions(requestOptions).setMethod(HttpMethod.POST))
      .compose(request -> {
        return request.send(body).compose(HttpClientResponse::end);
      }).await();
  }
}
