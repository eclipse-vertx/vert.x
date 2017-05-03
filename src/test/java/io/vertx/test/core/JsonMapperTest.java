/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.json.Json;
import org.junit.Test;

import java.time.Instant;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonMapperTest extends VertxTestBase {

  @Test
  public void testGetSetMapper() {
    ObjectMapper mapper = Json.mapper;
    assertNotNull(mapper);
    ObjectMapper newMapper = new ObjectMapper();
    Json.mapper = newMapper;
    assertSame(newMapper, Json.mapper);
    Json.mapper = mapper;
  }

  @Test
  public void testGetSetPrettyMapper() {
    ObjectMapper mapper = Json.prettyMapper;
    assertNotNull(mapper);
    ObjectMapper newMapper = new ObjectMapper();
    Json.prettyMapper = newMapper;
    assertSame(newMapper, Json.prettyMapper);
    Json.prettyMapper = mapper;
  }

  @Test
  public void encodeCustomTypeInstant() {
    Instant now = Instant.now();
    String json = Json.encode(now);
    assertNotNull(json);
    // the RFC is one way only
    Instant decoded = Instant.from(ISO_INSTANT.parse(json.substring(1, json.length() - 1)));
    assertEquals(now, decoded);

  }

  @Test
  public void encodeCustomTypeInstantNull() {
    Instant now = null;
    String json = Json.encode(now);
    assertNotNull(json);
    assertEquals("null", json);
  }

  @Test
  public void encodeCustomTypeBinary() {
    byte[] data = new byte[] { 'h', 'e', 'l', 'l', 'o'};
    String json = Json.encode(data);
    assertNotNull(json);
    // base64 encoded hello
    assertEquals("\"aGVsbG8=\"", json);
  }

  @Test
  public void encodeCustomTypeBinaryNull() {
    byte[] data = null;
    String json = Json.encode(data);
    assertNotNull(json);
    assertEquals("null", json);
  }
}
