/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.impl.HttpUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2SettingsTest extends HttpTestBase {

  long[] min = { 0, 0, 0, 0, 0x4000, 0 };
  long[] max = { 0xFFFFFFFFL, 1, 0xFFFFFFFFL, Integer.MAX_VALUE, 0xFFFFFF, Integer.MAX_VALUE };

  @Test
  public void testSettingsMin() {
    for (int i = 1;i <= 6;i++) {
      try {
        new Http2Settings().set(i, min[i - 1] - 1);
        fail();
      } catch (IllegalArgumentException ignore) {
      }
    }
    Http2Settings settings = new Http2Settings();
    for (int i = 1;i <= 6;i++) {
      settings.set(i, min[i - 1]);
    }
    HttpUtils.fromVertxSettings(settings);
  }

  @Test
  public void testSettinsMax() {
    for (int i = 1;i <= 6;i++) {
      try {
        new Http2Settings().set(i, max[i - 1] + 1);
        fail("Was expecting setting " + (i - 1) + " update to throw IllegalArgumentException");
      } catch (IllegalArgumentException ignore) {
      }
    }
    Http2Settings settings = new Http2Settings();
    for (int i = 1;i <= 6;i++) {
      settings.set(i, max[i - 1]);
    }
    HttpUtils.fromVertxSettings(settings);
  }

  @Test
  public void toNettySettings() {
    Http2Settings settings = new Http2Settings();
    for (int i = 7;i <= 0xFFFF;i += 1) {
      // we need to clamp the random value to pass validation
      settings.set(0xFFFF, Math.min(0xFFFFFFFFL, TestUtils.randomPositiveLong()));
    }
    io.netty.handler.codec.http2.Http2Settings conv = HttpUtils.fromVertxSettings(settings);
    for (int i = 1;i <= 0xFFFF;i += 1) {
      assertEquals(settings.get(i), conv.get((char)i));
    }
    settings = HttpUtils.toVertxSettings(conv);
    for (int i = 1;i <= 0xFFFF;i += 1) {
      assertEquals(settings.get(i), conv.get((char)i));
    }
  }

  @Test
  public void testSettings() {
    Http2Settings settings = new Http2Settings();

    assertEquals(true, settings.isPushEnabled());
    assertEquals(Http2Settings.DEFAULT_MAX_HEADER_LIST_SIZE, settings.getMaxHeaderListSize());
    assertEquals(Http2Settings.DEFAULT_MAX_CONCURRENT_STREAMS, settings.getMaxConcurrentStreams());
    assertEquals(Http2Settings.DEFAULT_INITIAL_WINDOW_SIZE, settings.getInitialWindowSize());
    assertEquals(Http2Settings.DEFAULT_MAX_FRAME_SIZE, settings.getMaxFrameSize());
    assertEquals(null, settings.getExtraSettings());

    Http2Settings update = TestUtils.randomHttp2Settings();
    assertFalse(settings.equals(update));
    assertNotSame(settings.hashCode(), settings.hashCode());
    assertSame(settings, settings.setHeaderTableSize(update.getHeaderTableSize()));
    assertEquals(settings.getHeaderTableSize(), update.getHeaderTableSize());
    assertSame(settings, settings.setPushEnabled(update.isPushEnabled()));
    assertEquals(settings.isPushEnabled(), update.isPushEnabled());
    assertSame(settings, settings.setMaxHeaderListSize(update.getMaxHeaderListSize()));
    assertEquals(settings.getMaxHeaderListSize(), update.getMaxHeaderListSize());
    assertSame(settings, settings.setMaxConcurrentStreams(update.getMaxConcurrentStreams()));
    assertEquals(settings.getMaxConcurrentStreams(), update.getMaxConcurrentStreams());
    assertSame(settings, settings.setInitialWindowSize(update.getInitialWindowSize()));
    assertEquals(settings.getInitialWindowSize(), update.getInitialWindowSize());
    assertSame(settings, settings.setMaxFrameSize(update.getMaxFrameSize()));
    assertEquals(settings.getMaxFrameSize(), update.getMaxFrameSize());
    assertSame(settings, settings.setExtraSettings(update.getExtraSettings()));
    Map<Integer, Long> extraSettings = new HashMap<>(update.getExtraSettings());
    assertEquals(update.getExtraSettings(), extraSettings);
    extraSettings.clear();
    assertEquals(update.getExtraSettings(), settings.getExtraSettings());
    assertTrue(settings.equals(update));
    assertEquals(settings.hashCode(), settings.hashCode());

    settings = new Http2Settings(update);
    assertEquals(settings.getHeaderTableSize(), update.getHeaderTableSize());
    assertEquals(settings.isPushEnabled(), update.isPushEnabled());
    assertEquals(settings.getMaxHeaderListSize(), update.getMaxHeaderListSize());
    assertEquals(settings.getMaxConcurrentStreams(), update.getMaxConcurrentStreams());
    assertEquals(settings.getInitialWindowSize(), update.getInitialWindowSize());
    assertEquals(settings.getMaxFrameSize(), update.getMaxFrameSize());
    assertEquals(update.getExtraSettings(), settings.getExtraSettings());
  }
}
