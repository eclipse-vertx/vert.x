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

package io.vertx.tests.json.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.ThreadingModel;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JacksonDatabindTest extends JacksonDatabindTestBase {

  @Override
  protected String writeValueAsString(Object o) throws Exception {
    return DatabindCodec.mapper().writeValueAsString(o);
  }

  @Override
  protected <T> T readValue(String content, Class<T> valueType) throws Exception {
    return DatabindCodec.mapper().readValue(content, valueType);
  }

  @Test
  public void testGetMapper() {
    ObjectMapper mapper = DatabindCodec.mapper();
    assertNotNull(mapper);
  }

  @Test
  public void testGenericDecoding() {
    Pojo original = new Pojo();
    original.value = "test";

    String json = Json.encode(Collections.singletonList(original));
    List<Pojo> correct;

    DatabindCodec databindCodec = new DatabindCodec();

    correct = databindCodec.fromString(json, new TypeReference<>() {
    });
    assertTrue(((List) correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    // same must apply if instead of string we use a buffer
    correct = databindCodec.fromBuffer(Buffer.buffer(json, "UTF8"), new TypeReference<>() {
    });
    assertTrue(((List) correct).get(0) instanceof Pojo);
    assertEquals(original.value, correct.get(0).value);

    List incorrect = Json.decodeValue(json, List.class);
    assertFalse(incorrect.get(0) instanceof Pojo);
    assertTrue(incorrect.get(0) instanceof Map);
    assertEquals(original.value, ((Map) (incorrect.get(0))).get("value"));
  }

  @Test
  public void testObjectMapperConfigAppliesToPrettyPrinting() {
    ObjectMapper om = DatabindCodec.mapper();
    SerializationConfig sc = om.getSerializationConfig();
    assertNotNull(sc);
    try {
      om.setConfig(sc.with(SerializationFeature.WRITE_ENUMS_USING_INDEX));
      ThreadingModel vt = ThreadingModel.VIRTUAL_THREAD;
      String expected = String.valueOf(vt.ordinal());
      assertEquals(expected, Json.encodePrettily(vt));
      assertEquals(expected, Json.encode(vt));
    } finally {
      om.setConfig(sc);
    }
  }
}
