package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.vertx.core.http.Http2Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Http2SettingInVertxConnectionHandlerBuilder {
  @Test
  public void testMaxHeaderListSizeIsNotIgnoredInNettySettings() {
    Http2Settings settings = new Http2Settings().setMaxHeaderListSize(Integer.MAX_VALUE);

    try {
      new VertxHttp2ConnectionHandlerBuilder<Http2ClientConnection>(null) {
        @Override
        protected VertxHttp2ConnectionHandler<Http2ClientConnection> build(
          Http2ConnectionDecoder decoder,
          Http2ConnectionEncoder encoder,
          io.netty.handler.codec.http2.Http2Settings nettySettings) throws Exception {

          assertEquals(Long.valueOf(Integer.MAX_VALUE), nettySettings.get(Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE));
          throw new EndOfTest();
        }
      }.initialSettings(settings).build();
    }catch(IllegalStateException e){
      if(!(e.getCause() instanceof EndOfTest)){
        throw e;
      }

      // Expected case
    }
  }

  private static class EndOfTest extends RuntimeException{}
}
