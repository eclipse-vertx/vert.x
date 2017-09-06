package io.vertx.core.http.impl;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.http.Http2Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpUtilsTest {

  @Test
  public void testMaxHeaderListSizeIsNotIgnoredWhenZero() {
    testMaxHeaderListSizeIsNotIgnoredInNettySettings(0);
  }

  @Test
  public void testMaxHeaderListSizeIsNotIgnoredWhenPositiveInteger() {
    testMaxHeaderListSizeIsNotIgnoredInNettySettings(new Double(Math.random() * 10000).intValue());
  }

  @Test
  public void testMaxHeaderListSizeIsNotIgnoredWhenIntegerMaxValue() {
    testMaxHeaderListSizeIsNotIgnoredInNettySettings(Integer.MAX_VALUE);
  }

  public void testMaxHeaderListSizeIsNotIgnoredInNettySettings(Integer maxHeaderListSize) {
    Http2Settings settings = new Http2Settings().setMaxHeaderListSize(maxHeaderListSize);
    io.netty.handler.codec.http2.Http2Settings nettySettings = new io.netty.handler.codec.http2.Http2Settings().maxHeaderListSize(new Double(Math.random() * 100000).intValue());
    HttpUtils.fromVertxInitialSettings(true, settings, nettySettings);
    assertEquals(Long.valueOf(maxHeaderListSize), nettySettings.get(Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE));
    assertEquals(Long.valueOf(maxHeaderListSize).longValue(), settings.getMaxHeaderListSize());
  }
}
