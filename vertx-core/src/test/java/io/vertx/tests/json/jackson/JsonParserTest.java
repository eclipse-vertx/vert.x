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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import io.vertx.core.json.jackson.JacksonCodec;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonParserTest {

  @Test
  public void testParseMap() throws Exception {
    JsonParser parser = JacksonCodec.createParser("{\"nested\":{\"key\":\"value\"},\"another\":4}");
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    Map<String, Object> nested = JacksonCodec.parseObject(parser);
    assertEquals(Map.of("key", "value"), nested);
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_NUMBER_INT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_END_OBJECT, parser.nextToken().id());
  }

  @Test
  public void testParseAny() throws Exception {
    JsonParser parser = JacksonCodec.createParser("{\"nested\":{\"key\":\"value\"},\"another\":4}");
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    Object nested = JacksonCodec.parseValue(parser);
    assertEquals(Map.of("key", "value"), nested);
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_NUMBER_INT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_END_OBJECT, parser.nextToken().id());
  }

  @Test
  public void testParseArray() throws Exception {
    JsonParser parser = JacksonCodec.createParser("{\"nested\":[0,1,2],\"another\":4}");
    assertEquals(JsonTokenId.ID_START_OBJECT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_START_ARRAY, parser.nextToken().id());
    Object nested = JacksonCodec.parseArray(parser);
    assertEquals(Arrays.asList(0, 1, 2), nested);
    assertEquals(JsonTokenId.ID_FIELD_NAME, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_NUMBER_INT, parser.nextToken().id());
    assertEquals(JsonTokenId.ID_END_OBJECT, parser.nextToken().id());
  }
}
