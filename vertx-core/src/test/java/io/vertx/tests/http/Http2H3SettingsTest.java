/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.http;

import io.netty.handler.codec.http3.Http3SettingsFrame;
import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.test.core.TestUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2H3SettingsTest {

  Long[] keys = new ArrayList<>(Http3Settings.SETTING_KEYS).toArray(new Long[0]);
  Map<Long, Long> min = Map.of(
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0L,
    Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, 0L,
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 0L,
    Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, 0L,
    Http3Settings.HTTP3_SETTINGS_H3_DATAGRAM, 0L,
    Http3Settings.HTTP3_SETTINGS_ENABLE_METADATA, 0L
  );
  Map<Long, Long> max = Map.of(
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0xFFFFFFFFL,
    Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, 0xFFFFFFFFL,
    Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 0xFFFFFFFFL,
    Http3Settings.HTTP3_SETTINGS_ENABLE_CONNECT_PROTOCOL, 0xFFFFFFFFL,
    Http3Settings.HTTP3_SETTINGS_H3_DATAGRAM, 0xFFFFFFFFL,
    Http3Settings.HTTP3_SETTINGS_ENABLE_METADATA, 0xFFFFFFFFL
  );

  @Test
  public void testSettingsMin() {
    for (Long key : keys) {
      try {
        new Http3Settings().set(key, min.get(key) - 1);
        fail();
      } catch (IllegalArgumentException ignore) {
      }
    }
    Http3Settings settings = new Http3Settings();
    for (Long key : keys) {
      settings.set(key, min.get(key));
    }
    HttpUtils.fromVertxSettings(settings);
  }

  @Test
  public void testSettingsMax() {
    for (Long key : keys) {
      try {
        new Http3Settings().set(key, max.get(key) + 1);
        fail("Was expecting setting " + (key - 1) + " update to throw IllegalArgumentException");
      } catch (IllegalArgumentException ignore) {
      }
    }
    Http3Settings settings = new Http3Settings();
    for (Long key : keys) {
      settings.set(key, max.get(key));
    }
    HttpUtils.fromVertxSettings(settings);
  }

  @Test
  public void toNettySettings() {
    Http3Settings settings = new Http3Settings();
    for (Long key : keys) {
      settings.set(key, Math.min(0xFFFFFFFFL, TestUtils.randomPositiveLong()));
    }
    settings.set(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, Math.min(Integer.MAX_VALUE,
      TestUtils.randomPositiveLong()));
    settings.set(1000, Math.min(0xFFFFFFFFL, TestUtils.randomPositiveLong()));

    Http3SettingsFrame conv = HttpUtils.fromVertxSettings(settings);
    for (Long key : keys) {
      assertEquals(settings.get(key), conv.get(key));
    }
    assertNull(conv.get(1000));

    settings = HttpUtils.toVertxSettings(conv);
    for (Long key : keys) {
      assertEquals(settings.get(key), conv.get(key));
    }
    assertNull(settings.get(1000));
  }

  @Test
  public void testSettings() {
    Http3Settings settings = new Http3Settings();

    assertEquals(Http3Settings.DEFAULT_QPACK_MAX_TABLE_CAPACITY, settings.getQpackMaxTableCapacity());
    assertEquals(Http3Settings.DEFAULT_MAX_FIELD_SECTION_SIZE, settings.getMaxFieldSectionSize());
    assertEquals(Http3Settings.DEFAULT_QPACK_BLOCKED_STREAMS, settings.getQpackMaxBlockedStreams());
    assertEquals(Http3Settings.DEFAULT_ENABLE_CONNECT_PROTOCOL, settings.getEnableConnectProtocol());
    assertEquals(Http3Settings.DEFAULT_H3_DATAGRAM, settings.getH3Datagram());
    assertEquals(Http3Settings.DEFAULT_ENABLE_METADATA, settings.getEnableMetadata());

    assertEquals(null, settings.getExtraSettings());

    Http3Settings update = TestUtils.randomHttp3Settings();
    assertFalse(settings.equals(update));
    assertNotSame(settings.hashCode(), settings.hashCode());
    assertSame(settings, settings.setMaxFieldSectionSize(update.getMaxFieldSectionSize()));
    assertEquals(settings.getMaxFieldSectionSize(), update.getMaxFieldSectionSize());
    assertSame(settings, settings.setQpackMaxTableCapacity(update.getQpackMaxTableCapacity()));
    assertEquals(settings.getQpackMaxTableCapacity(), update.getQpackMaxTableCapacity());
    assertSame(settings, settings.setQpackMaxBlockedStreams(update.getQpackMaxBlockedStreams()));
    assertEquals(settings.getQpackMaxBlockedStreams(), update.getQpackMaxBlockedStreams());
    assertSame(settings, settings.setH3Datagram(update.getH3Datagram()));
    assertEquals(settings.getH3Datagram(), update.getH3Datagram());
    assertSame(settings, settings.setEnableConnectProtocol(update.getEnableConnectProtocol()));
    assertEquals(settings.getEnableConnectProtocol(), update.getEnableConnectProtocol());
    assertSame(settings, settings.setEnableMetadata(update.getEnableMetadata()));
    assertEquals(settings.getEnableMetadata(), update.getEnableMetadata());
    assertSame(settings, settings.setExtraSettings(update.getExtraSettings()));
    Map<Long, Long> extraSettings = new HashMap<>(update.getExtraSettings());
    assertEquals(update.getExtraSettings(), extraSettings);
    extraSettings.clear();
    assertEquals(update.getExtraSettings(), settings.getExtraSettings());
    assertTrue(settings.equals(update));
    assertEquals(settings.hashCode(), settings.hashCode());

    settings = new Http3Settings(update);
    assertEquals(settings.getMaxFieldSectionSize(), update.getMaxFieldSectionSize());
    assertEquals(settings.getQpackMaxTableCapacity(), update.getQpackMaxTableCapacity());
    assertEquals(settings.getQpackMaxBlockedStreams(), update.getQpackMaxBlockedStreams());
    assertEquals(settings.getH3Datagram(), update.getH3Datagram());
    assertEquals(settings.getEnableConnectProtocol(), update.getEnableConnectProtocol());
    assertEquals(settings.getEnableMetadata(), update.getEnableMetadata());
    assertEquals(update.getExtraSettings(), settings.getExtraSettings());
  }

  @Test
  public void testEqualsHashCode() throws Exception {
    Http3Settings s1 = new Http3Settings().setMaxFieldSectionSize(1024);
    Http3Settings s2 = new Http3Settings().setMaxFieldSectionSize(1024);
    Http3Settings s3 = new Http3Settings(s1.toJson());
    Http3Settings s4 = new Http3Settings().setMaxFieldSectionSize(2048);

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
