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

import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.impl.HttpUtils;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2Test extends VertxTestBase {

  long[] min = { 0, 0, 0, 0, 0x4000, 0 };
  long[] max = { Integer.MAX_VALUE, 1, 0xFFFFFFFFL, Integer.MAX_VALUE, 0xFFFFFF, Integer.MAX_VALUE };

  @Test
  public void testMin() {
    for (int i = 1;i <= 6;i++) {
      try {
        new Http2Settings().put(i, min[i - 1] - 1);
        fail();
      } catch (IllegalArgumentException ignore) {
      }
    }
    Http2Settings settings = new Http2Settings();
    for (int i = 1;i <= 6;i++) {
      settings.put(i, min[i - 1]);
    }
    HttpUtils.fromVertxSettings(settings);
  }

  @Test
  public void testMax() {
    for (int i = 1;i <= 6;i++) {
      try {
        new Http2Settings().put(i, max[i - 1] + 1);
        System.out.println("i = " + i);
        fail();
      } catch (IllegalArgumentException ignore) {
      }
    }
    Http2Settings settings = new Http2Settings();
    for (int i = 1;i <= 6;i++) {
      settings.put(i, max[i - 1]);
    }
    HttpUtils.fromVertxSettings(settings);
  }

  @Test
  public void toNettySettings() {
    Http2Settings settings = new Http2Settings();
    for (int i = 7;i <= 0xFFFF;i += 1) {
      settings.put(0xFFFF, TestUtils.randomPositiveLong());
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
}
