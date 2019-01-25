/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import io.vertx.core.http.impl.HttpUtils;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Thomas Segismont
 */
public class Http2SettingsTest {

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

  @Test
  public void testEqualsHashCode() throws Exception {
    Http2Settings s1 = new Http2Settings().setHeaderTableSize(1024);
    Http2Settings s2 = new Http2Settings().setHeaderTableSize(1024);
    Http2Settings s3 = new Http2Settings(s1.toJson());
    Http2Settings s4 = new Http2Settings().setHeaderTableSize(2048);

    assertEquals(s1, s1);
    assertEquals(s2, s2);
    assertEquals(s3, s3);

    assertEquals(s1, s2);
    assertEquals(s2, s1);
    assertEquals(s2, s3);
    assertEquals(s3, s2);

    assertEquals(s1, s3);
    assertEquals(s3, s1);

    assertEquals(s1.hashCode(), s2.hashCode());
    assertEquals(s2.hashCode(), s3.hashCode());

    assertFalse(s1.equals(null));
    assertFalse(s2.equals(null));
    assertFalse(s3.equals(null));

    assertNotEquals(s1, s4);
    assertNotEquals(s4, s1);
    assertNotEquals(s2, s4);
    assertNotEquals(s4, s2);
    assertNotEquals(s3, s4);
    assertNotEquals(s4, s3);

    assertNotEquals(s1.hashCode(), s4.hashCode());
    assertNotEquals(s2.hashCode(), s4.hashCode());
    assertNotEquals(s3.hashCode(), s4.hashCode());
  }
}
