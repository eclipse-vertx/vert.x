/*
 * Copyright (c) 2011-2016 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.Objects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonTest {

  @Before
  public void setUp() {
    Json.initialize();
  }

  @After
  public void tearDown() {
    Json.initialize();
  }

  @Test
  public void testRegisterModuleEncode() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(TestObject.class, new TestObjectSerializer());
    Json.registerModule(module);

    TestObject test = new TestObject(5, "aaa");
    String expected = "\"5#aaa\"";
    assertEquals(expected, Json.encode(test));
  }

  @Test
  public void testRegisterModuleDecode() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(TestObject.class, new TestObjectDeserializer());
    Json.registerModule(module);

    TestObject test = Json.decodeValue("\"10#bbb\"", TestObject.class);
    TestObject expected = new TestObject(10, "bbb");
    assertEquals(expected, test);
  }

  private static class TestObjectSerializer extends JsonSerializer<TestObject> {

    @Override
    public void serialize(TestObject value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
      String serializedValue = value.field1 + "#" + value.field2;
      jgen.writeString(serializedValue);
    }
  }

  private class TestObjectDeserializer extends JsonDeserializer<TestObject> {

    @Override
    public TestObject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      JsonNode node = p.getCodec().readTree(p);
      String split[] = node.asText().split("#");
      return new TestObject(Integer.parseInt(split[0]), split[1]);
    }

  }

  private static class TestObject {

    private int field1;
    private String field2;

    public TestObject(int field1, String field2) {
      this.field1 = field1;
      this.field2 = field2;
    }

    @Override
    public int hashCode() {
      int hash = 5;
      hash = 89 * hash + this.field1;
      hash = 89 * hash + Objects.hashCode(this.field2);
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final TestObject other = (TestObject) obj;
      if (this.field1 != other.field1) {
        return false;
      }
      if (!Objects.equals(this.field2, other.field2)) {
        return false;
      }
      return true;
    }
  }
}
