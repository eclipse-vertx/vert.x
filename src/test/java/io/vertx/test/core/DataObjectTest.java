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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.codegen.AggregatedDataObject;
import io.vertx.test.codegen.ChildInheritingDataObject;
import io.vertx.test.codegen.ChildInheritingDataObjectConverter;
import io.vertx.test.codegen.ChildNotInheritingDataObject;
import io.vertx.test.codegen.ChildNotInheritingDataObjectConverter;
import io.vertx.test.codegen.NoConverterDataObject;
import io.vertx.test.codegen.TestDataObject;
import io.vertx.test.codegen.TestDataObjectConverter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class DataObjectTest extends VertxTestBase {

  private static JsonObject toJson(Map<String, Object> map) {
    JsonObject json = new JsonObject();
    map.forEach(json::put);
    return json;
  }

  @Test
  public void testJsonToDataObject() {

    String key = TestUtils.randomAlphaString(10);
    String stringValue = TestUtils.randomAlphaString(20);
    boolean booleanValue = TestUtils.randomBoolean();
    byte byteValue = TestUtils.randomByte();
    short shortValue = TestUtils.randomShort();
    int intValue = TestUtils.randomInt();
    long longValue = TestUtils.randomLong();
    float floatValue = TestUtils.randomFloat();
    double doubleValue = TestUtils.randomDouble();
    char charValue = TestUtils.randomChar();
    Boolean boxedBooleanValue = TestUtils.randomBoolean();
    Byte boxedByteValue = TestUtils.randomByte();
    Short boxedShortValue = TestUtils.randomShort();
    Integer boxedIntValue = TestUtils.randomInt();
    Long boxedLongValue = TestUtils.randomLong();
    Float boxedFloatValue = TestUtils.randomFloat();
    Double boxedDoubleValue = TestUtils.randomDouble();
    Character boxedCharValue = TestUtils.randomChar();
    AggregatedDataObject aggregatedDataObject = new AggregatedDataObject().setValue(TestUtils.randomAlphaString(20));
    Buffer buffer = TestUtils.randomBuffer(20);
    JsonObject jsonObject = new JsonObject().put("wibble", TestUtils.randomAlphaString(20));
    JsonArray jsonArray = new JsonArray().add(TestUtils.randomAlphaString(20));
    HttpMethod httpMethod = HttpMethod.values()[TestUtils.randomPositiveInt() % HttpMethod.values().length];
    Map<String, Object> map = new HashMap<>();
    map.put(TestUtils.randomAlphaString(10), TestUtils.randomAlphaString(20));
    map.put(TestUtils.randomAlphaString(10), TestUtils.randomBoolean());
    map.put(TestUtils.randomAlphaString(10), TestUtils.randomInt());
    List<Object> list = new ArrayList<>();
    list.add(TestUtils.randomAlphaString(20));
    list.add(TestUtils.randomBoolean());
    list.add(TestUtils.randomInt());

    JsonObject json = new JsonObject();
    json.put("stringValue", stringValue);
    json.put("booleanValue", booleanValue);
    json.put("byteValue", byteValue);
    json.put("shortValue", shortValue);
    json.put("intValue", intValue);
    json.put("longValue", longValue);
    json.put("floatValue", floatValue);
    json.put("doubleValue", doubleValue);
    json.put("charValue", Character.toString(charValue));
    json.put("boxedBooleanValue", boxedBooleanValue);
    json.put("boxedByteValue", boxedByteValue);
    json.put("boxedShortValue", boxedShortValue);
    json.put("boxedIntValue", boxedIntValue);
    json.put("boxedLongValue", boxedLongValue);
    json.put("boxedFloatValue", boxedFloatValue);
    json.put("boxedDoubleValue", boxedDoubleValue);
    json.put("boxedCharValue", Character.toString(boxedCharValue));
    json.put("aggregatedDataObject", new JsonObject().put("value", aggregatedDataObject.getValue()));
    json.put("buffer", Base64.getEncoder().encodeToString(buffer.getBytes()));
    json.put("jsonObject", jsonObject);
    json.put("jsonArray", jsonArray);
    json.put("httpMethod", httpMethod.toString());
    json.put("stringValues", new JsonArray().add(stringValue));
    json.put("boxedBooleanValues", new JsonArray().add(boxedBooleanValue));
    json.put("boxedByteValues", new JsonArray().add(boxedByteValue));
    json.put("boxedShortValues", new JsonArray().add(boxedShortValue));
    json.put("boxedIntValues", new JsonArray().add(boxedIntValue));
    json.put("boxedLongValues", new JsonArray().add(boxedLongValue));
    json.put("boxedFloatValues", new JsonArray().add(boxedFloatValue));
    json.put("boxedDoubleValues", new JsonArray().add(boxedDoubleValue));
    json.put("boxedCharValues", new JsonArray().add(Character.toString(boxedCharValue)));
    json.put("aggregatedDataObjects", new JsonArray().add(new JsonObject().put("value", aggregatedDataObject.getValue())));
    json.put("buffers", new JsonArray().add(Base64.getEncoder().encodeToString(buffer.getBytes())));
    json.put("jsonObjects", new JsonArray().add(jsonObject));
    json.put("jsonArrays", new JsonArray().add(jsonArray));
    json.put("httpMethods", new JsonArray().add(httpMethod.toString()));
    json.put("objects", new JsonArray().add(list.get(0)).add(list.get(1)).add(list.get(2)));
    json.put("addedStringValues", new JsonArray().add(stringValue));
    json.put("addedBooleanValues", new JsonArray().add(boxedBooleanValue));
    json.put("addedByteValues", new JsonArray().add(boxedByteValue));
    json.put("addedShortValues", new JsonArray().add(boxedShortValue));
    json.put("addedIntValues", new JsonArray().add(boxedIntValue));
    json.put("addedLongValues", new JsonArray().add(boxedLongValue));
    json.put("addedFloatValues", new JsonArray().add(boxedFloatValue));
    json.put("addedDoubleValues", new JsonArray().add(boxedDoubleValue));
    json.put("addedCharValues", new JsonArray().add(Character.toString(boxedCharValue)));
    json.put("addedBoxedBooleanValues", new JsonArray().add(boxedBooleanValue));
    json.put("addedBoxedByteValues", new JsonArray().add(boxedByteValue));
    json.put("addedBoxedShortValues", new JsonArray().add(boxedShortValue));
    json.put("addedBoxedIntValues", new JsonArray().add(boxedIntValue));
    json.put("addedBoxedLongValues", new JsonArray().add(boxedLongValue));
    json.put("addedBoxedFloatValues", new JsonArray().add(boxedFloatValue));
    json.put("addedBoxedDoubleValues", new JsonArray().add(boxedDoubleValue));
    json.put("addedBoxedCharValues", new JsonArray().add(Character.toString(boxedCharValue)));
    json.put("addedAggregatedDataObjects", new JsonArray().add(new JsonObject().put("value", aggregatedDataObject.getValue())));
    json.put("addedBuffers", new JsonArray().add(Base64.getEncoder().encodeToString(buffer.getBytes())));
    json.put("addedJsonObjects", new JsonArray().add(jsonObject));
    json.put("addedJsonArrays", new JsonArray().add(jsonArray));
    json.put("addedHttpMethods", new JsonArray().add(httpMethod.toString()));
    json.put("addedObjects", new JsonArray().add(list.get(0)).add(list.get(1)).add(list.get(2)));
    json.put("stringValueMap", new JsonObject().put(key, stringValue));
    json.put("boxedBooleanValueMap", new JsonObject().put(key, boxedBooleanValue));
    json.put("boxedByteValueMap", new JsonObject().put(key, boxedByteValue));
    json.put("boxedShortValueMap", new JsonObject().put(key, boxedShortValue));
    json.put("boxedIntValueMap", new JsonObject().put(key, boxedIntValue));
    json.put("boxedLongValueMap", new JsonObject().put(key, boxedLongValue));
    json.put("boxedFloatValueMap", new JsonObject().put(key, boxedFloatValue));
    json.put("boxedDoubleValueMap", new JsonObject().put(key, boxedDoubleValue));
    json.put("boxedCharValueMap", new JsonObject().put(key, Character.toString(boxedCharValue)));
    json.put("aggregatedDataObjectMap", new JsonObject().put(key, new JsonObject().put("value", aggregatedDataObject.getValue())));
    json.put("bufferMap", new JsonObject().put(key, Base64.getEncoder().encodeToString(buffer.getBytes())));
    json.put("jsonObjectMap", new JsonObject().put(key, jsonObject));
    json.put("jsonArrayMap", new JsonObject().put(key, jsonArray));
    json.put("httpMethodMap", new JsonObject().put(key, httpMethod.toString()));
    json.put("objectMap", toJson(map));

    TestDataObject obj = new TestDataObject();
    TestDataObjectConverter.fromJson(json, obj);

    assertEquals(stringValue, obj.getStringValue());
    assertEquals(booleanValue, obj.isBooleanValue());
    assertEquals(byteValue, obj.getByteValue());
    assertEquals(shortValue, obj.getShortValue());
    assertEquals(intValue, obj.getIntValue());
    assertEquals(longValue, obj.getLongValue());
    assertEquals(floatValue, obj.getFloatValue(), 0);
    assertEquals(doubleValue, obj.getDoubleValue(), 0);
    assertEquals(charValue, obj.getCharValue());
    assertEquals(boxedBooleanValue, obj.isBoxedBooleanValue());
    assertEquals(boxedByteValue, obj.getBoxedByteValue());
    assertEquals(boxedShortValue, obj.getBoxedShortValue());
    assertEquals(boxedIntValue, obj.getBoxedIntValue());
    assertEquals(boxedLongValue, obj.getBoxedLongValue());
    assertEquals(boxedFloatValue, obj.getBoxedFloatValue(), 0);
    assertEquals(boxedDoubleValue, obj.getBoxedDoubleValue(), 0);
    assertEquals(boxedCharValue, obj.getBoxedCharValue());
    assertEquals(aggregatedDataObject, obj.getAggregatedDataObject());
    assertEquals(buffer, obj.getBuffer());
    assertEquals(jsonObject, obj.getJsonObject());
    assertEquals(jsonArray, obj.getJsonArray());
    assertEquals(httpMethod, obj.getHttpMethod());
    assertEquals(Collections.singletonList(stringValue), obj.getStringValues());
    assertEquals(Collections.singletonList(boxedBooleanValue), obj.getBoxedBooleanValues());
    assertEquals(Collections.singletonList(boxedByteValue), obj.getBoxedByteValues());
    assertEquals(Collections.singletonList(boxedShortValue), obj.getBoxedShortValues());
    assertEquals(Collections.singletonList(boxedIntValue), obj.getBoxedIntValues());
    assertEquals(Collections.singletonList(boxedLongValue), obj.getBoxedLongValues());
    assertEquals(Collections.singletonList(boxedFloatValue), obj.getBoxedFloatValues());
    assertEquals(Collections.singletonList(boxedDoubleValue), obj.getBoxedDoubleValues());
    assertEquals(Collections.singletonList(boxedCharValue), obj.getBoxedCharValues());
    assertEquals(Collections.singletonList(aggregatedDataObject), obj.getAggregatedDataObjects());
    assertEquals(Collections.singletonList(buffer), obj.getBuffers());
    assertEquals(Collections.singletonList(jsonObject), obj.getJsonObjects());
    assertEquals(Collections.singletonList(jsonArray), obj.getJsonArrays());
    assertEquals(Collections.singletonList(httpMethod), obj.getHttpMethods());
    assertEquals(list, obj.getObjects());
    assertEquals(Collections.singletonList(stringValue), obj.getAddedStringValues());
    assertEquals(Collections.singletonList(boxedBooleanValue), obj.getAddedBooleanValues());
    assertEquals(Collections.singletonList(boxedByteValue), obj.getAddedByteValues());
    assertEquals(Collections.singletonList(boxedShortValue), obj.getAddedShortValues());
    assertEquals(Collections.singletonList(boxedIntValue), obj.getAddedIntValues());
    assertEquals(Collections.singletonList(boxedLongValue), obj.getAddedLongValues());
    assertEquals(Collections.singletonList(boxedFloatValue), obj.getAddedFloatValues());
    assertEquals(Collections.singletonList(boxedDoubleValue), obj.getAddedDoubleValues());
    assertEquals(Collections.singletonList(boxedCharValue), obj.getAddedCharValues());
    assertEquals(Collections.singletonList(boxedBooleanValue), obj.getAddedBoxedBooleanValues());
    assertEquals(Collections.singletonList(boxedByteValue), obj.getAddedBoxedByteValues());
    assertEquals(Collections.singletonList(boxedShortValue), obj.getAddedBoxedShortValues());
    assertEquals(Collections.singletonList(boxedIntValue), obj.getAddedBoxedIntValues());
    assertEquals(Collections.singletonList(boxedLongValue), obj.getAddedBoxedLongValues());
    assertEquals(Collections.singletonList(boxedFloatValue), obj.getAddedBoxedFloatValues());
    assertEquals(Collections.singletonList(boxedDoubleValue), obj.getAddedBoxedDoubleValues());
    assertEquals(Collections.singletonList(boxedCharValue), obj.getAddedBoxedCharValues());
    assertEquals(Collections.singletonList(aggregatedDataObject), obj.getAddedAggregatedDataObjects());
    assertEquals(Collections.singletonList(buffer), obj.getAddedBuffers());
    assertEquals(Collections.singletonList(jsonObject), obj.getAddedJsonObjects());
    assertEquals(Collections.singletonList(jsonArray), obj.getAddedJsonArrays());
    assertEquals(Collections.singletonList(httpMethod), obj.getAddedHttpMethods());
    assertEquals(list, obj.getAddedObjects());
    assertEquals(Collections.singletonMap(key, stringValue), obj.getStringValueMap());
    assertEquals(Collections.singletonMap(key, boxedBooleanValue), obj.getBoxedBooleanValueMap());
    assertEquals(Collections.singletonMap(key, boxedByteValue), obj.getBoxedByteValueMap());
    assertEquals(Collections.singletonMap(key, boxedShortValue), obj.getBoxedShortValueMap());
    assertEquals(Collections.singletonMap(key, boxedIntValue), obj.getBoxedIntValueMap());
    assertEquals(Collections.singletonMap(key, boxedLongValue), obj.getBoxedLongValueMap());
    assertEquals(Collections.singletonMap(key, boxedFloatValue), obj.getBoxedFloatValueMap());
    assertEquals(Collections.singletonMap(key, boxedDoubleValue), obj.getBoxedDoubleValueMap());
    assertEquals(Collections.singletonMap(key, boxedCharValue), obj.getBoxedCharValueMap());
    assertEquals(Collections.singletonMap(key, aggregatedDataObject), obj.getAggregatedDataObjectMap());
    assertEquals(Collections.singletonMap(key, buffer), obj.getBufferMap());
    assertEquals(Collections.singletonMap(key, jsonObject), obj.getJsonObjectMap());
    assertEquals(Collections.singletonMap(key, jsonArray), obj.getJsonArrayMap());
    assertEquals(Collections.singletonMap(key, httpMethod), obj.getHttpMethodMap());
    assertEquals(map, obj.getObjectMap());

    // Sometimes json can use java collections so test it runs fine in this case
    json = new JsonObject();
    json.put("aggregatedDataObject", new JsonObject().put("value", aggregatedDataObject.getValue()).getMap());
    json.put("aggregatedDataObjects", new JsonArray().add(new JsonObject().put("value", aggregatedDataObject.getValue()).getMap()));
    json.put("addedAggregatedDataObjects", new JsonArray().add(new JsonObject().put("value", aggregatedDataObject.getValue()).getMap()));
    obj = new TestDataObject();
    TestDataObjectConverter.fromJson(json, obj);
    assertEquals(aggregatedDataObject, obj.getAggregatedDataObject());
    assertEquals(Collections.singletonList(aggregatedDataObject), obj.getAggregatedDataObjects());
    assertEquals(Collections.singletonList(aggregatedDataObject), obj.getAddedAggregatedDataObjects());
  }

  @Test
  public void testEmptyJsonToDataObject() {

    JsonObject json = new JsonObject();

    TestDataObject obj = new TestDataObject();
    TestDataObjectConverter.fromJson(json, obj);

    assertEquals(null, obj.getStringValue());
    assertEquals(false, obj.isBooleanValue());
    assertEquals(0, obj.getByteValue());
    assertEquals(0, obj.getShortValue());
    assertEquals(0, obj.getIntValue());
    assertEquals(0l, obj.getLongValue());
    assertEquals(0f, obj.getFloatValue(), 0);
    assertEquals(0d, obj.getDoubleValue(), 0);
    assertEquals((char)0, obj.getCharValue());
    assertEquals(null, obj.isBoxedBooleanValue());
    assertEquals(null, obj.getBoxedByteValue());
    assertEquals(null, obj.getBoxedShortValue());
    assertEquals(null, obj.getBoxedIntValue());
    assertEquals(null, obj.getBoxedLongValue());
    assertEquals(null, obj.getBoxedFloatValue());
    assertEquals(null, obj.getBoxedDoubleValue());
    assertEquals(null, obj.getBoxedCharValue());
    assertEquals(null, obj.getAggregatedDataObject());
    assertEquals(null, obj.getBuffer());
    assertEquals(null, obj.getJsonObject());
    assertEquals(null, obj.getJsonArray());
    assertEquals(null, obj.getStringValues());
    assertEquals(null, obj.getBoxedBooleanValues());
    assertEquals(null, obj.getBoxedByteValues());
    assertEquals(null, obj.getBoxedShortValues());
    assertEquals(null, obj.getBoxedIntValues());
    assertEquals(null, obj.getBoxedLongValues());
    assertEquals(null, obj.getBoxedFloatValues());
    assertEquals(null, obj.getBoxedDoubleValues());
    assertEquals(null, obj.getBoxedCharValues());
    assertEquals(null, obj.getAggregatedDataObjects());
    assertEquals(null, obj.getBuffers());
    assertEquals(null, obj.getJsonObjects());
    assertEquals(null, obj.getJsonArrays());
    assertEquals(null, obj.getHttpMethods());
    assertEquals(null, obj.getObjects());
    assertEquals(Collections.emptyList(), obj.getAddedStringValues());
    assertEquals(Collections.emptyList(), obj.getAddedBooleanValues());
    assertEquals(Collections.emptyList(), obj.getAddedByteValues());
    assertEquals(Collections.emptyList(), obj.getAddedShortValues());
    assertEquals(Collections.emptyList(), obj.getAddedIntValues());
    assertEquals(Collections.emptyList(), obj.getAddedLongValues());
    assertEquals(Collections.emptyList(), obj.getAddedFloatValues());
    assertEquals(Collections.emptyList(), obj.getAddedDoubleValues());
    assertEquals(Collections.emptyList(), obj.getAddedCharValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedBooleanValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedByteValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedShortValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedIntValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedLongValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedFloatValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedDoubleValues());
    assertEquals(Collections.emptyList(), obj.getAddedBoxedCharValues());
    assertEquals(Collections.emptyList(), obj.getAddedAggregatedDataObjects());
    assertEquals(Collections.emptyList(), obj.getAddedBuffers());
    assertEquals(Collections.emptyList(), obj.getAddedJsonObjects());
    assertEquals(Collections.emptyList(), obj.getAddedJsonArrays());
    assertEquals(Collections.emptyList(), obj.getAddedHttpMethods());
    assertEquals(Collections.emptyList(), obj.getAddedObjects());
    assertEquals(null, obj.getStringValueMap());
    assertEquals(null, obj.getBoxedBooleanValueMap());
    assertEquals(null, obj.getBoxedByteValueMap());
    assertEquals(null, obj.getBoxedShortValueMap());
    assertEquals(null, obj.getBoxedIntValueMap());
    assertEquals(null, obj.getBoxedLongValueMap());
    assertEquals(null, obj.getBoxedFloatValueMap());
    assertEquals(null, obj.getBoxedDoubleValueMap());
    assertEquals(null, obj.getBoxedCharValueMap());
    assertEquals(null, obj.getAggregatedDataObjectMap());
    assertEquals(null, obj.getBufferMap());
    assertEquals(null, obj.getJsonObjectMap());
    assertEquals(null, obj.getJsonArrayMap());
    assertEquals(null, obj.getHttpMethodMap());
    assertEquals(null, obj.getObjectMap());
  }

  @Test
  public void testDataObjectToJson() {
    String key = TestUtils.randomAlphaString(10);
    String stringValue = TestUtils.randomAlphaString(20);
    boolean booleanValue = TestUtils.randomBoolean();
    byte byteValue = TestUtils.randomByte();
    short shortValue = TestUtils.randomShort();
    int intValue = TestUtils.randomInt();
    long longValue = TestUtils.randomLong();
    float floatValue = TestUtils.randomFloat();
    double doubleValue = TestUtils.randomDouble();
    char charValue = TestUtils.randomChar();
    Boolean boxedBooleanValue = TestUtils.randomBoolean();
    Byte boxedByteValue = TestUtils.randomByte();
    Short boxedShortValue = TestUtils.randomShort();
    Integer boxedIntValue = TestUtils.randomInt();
    Long boxedLongValue = TestUtils.randomLong();
    Float boxedFloatValue = TestUtils.randomFloat();
    Double boxedDoubleValue = TestUtils.randomDouble();
    Character boxedCharValue = TestUtils.randomChar();
    AggregatedDataObject aggregatedDataObject = new AggregatedDataObject().setValue(TestUtils.randomAlphaString(20));
    Buffer buffer = TestUtils.randomBuffer(20);
    JsonObject jsonObject = new JsonObject().put("wibble", TestUtils.randomAlphaString(20));
    JsonArray jsonArray = new JsonArray().add(TestUtils.randomAlphaString(20));
    HttpMethod httpMethod = HttpMethod.values()[TestUtils.randomPositiveInt() % HttpMethod.values().length];
    Map<String, Object> map = new HashMap<>();
    map.put(TestUtils.randomAlphaString(10), TestUtils.randomAlphaString(20));
    map.put(TestUtils.randomAlphaString(10), TestUtils.randomBoolean());
    map.put(TestUtils.randomAlphaString(10), TestUtils.randomInt());
    List<Object> list = new ArrayList<>();
    list.add(TestUtils.randomAlphaString(20));
    list.add(TestUtils.randomBoolean());
    list.add(TestUtils.randomInt());

    TestDataObject obj = new TestDataObject();
    obj.setStringValue(stringValue);
    obj.setBooleanValue(booleanValue);
    obj.setByteValue(byteValue);
    obj.setShortValue(shortValue);
    obj.setIntValue(intValue);
    obj.setLongValue(longValue);
    obj.setFloatValue(floatValue);
    obj.setDoubleValue(doubleValue);
    obj.setCharValue(charValue);
    obj.setBoxedBooleanValue(boxedBooleanValue);
    obj.setBoxedByteValue(boxedByteValue);
    obj.setBoxedShortValue(boxedShortValue);
    obj.setBoxedIntValue(boxedIntValue);
    obj.setBoxedLongValue(boxedLongValue);
    obj.setBoxedFloatValue(boxedFloatValue);
    obj.setBoxedDoubleValue(boxedDoubleValue);
    obj.setBoxedCharValue(boxedCharValue);
    obj.setAggregatedDataObject(aggregatedDataObject);
    obj.setBuffer(buffer);
    obj.setJsonObject(jsonObject);
    obj.setJsonArray(jsonArray);
    obj.setHttpMethod(httpMethod);
    obj.setStringValues(Collections.singletonList(stringValue));
    obj.setBoxedBooleanValues(Collections.singletonList(boxedBooleanValue));
    obj.setBoxedByteValues(Collections.singletonList(boxedByteValue));
    obj.setBoxedShortValues(Collections.singletonList(boxedShortValue));
    obj.setBoxedIntValues(Collections.singletonList(boxedIntValue));
    obj.setBoxedLongValues(Collections.singletonList(boxedLongValue));
    obj.setBoxedFloatValues(Collections.singletonList(boxedFloatValue));
    obj.setBoxedDoubleValues(Collections.singletonList(boxedDoubleValue));
    obj.setBoxedCharValues(Collections.singletonList(boxedCharValue));
    obj.setAggregatedDataObjects(Collections.singletonList(aggregatedDataObject));
    obj.setBuffers(Collections.singletonList(buffer));
    obj.setJsonObjects(Collections.singletonList(jsonObject));
    obj.setJsonArrays(Collections.singletonList(jsonArray));
    obj.setHttpMethods(Collections.singletonList(httpMethod));
    obj.setObjects(list);
    obj.setStringValueMap(Collections.singletonMap(key, stringValue));
    obj.setBoxedBooleanValueMap(Collections.singletonMap(key, boxedBooleanValue));
    obj.setBoxedByteValueMap(Collections.singletonMap(key, boxedByteValue));
    obj.setBoxedShortValueMap(Collections.singletonMap(key, boxedShortValue));
    obj.setBoxedIntValueMap(Collections.singletonMap(key, boxedIntValue));
    obj.setBoxedLongValueMap(Collections.singletonMap(key, boxedLongValue));
    obj.setBoxedFloatValueMap(Collections.singletonMap(key, boxedFloatValue));
    obj.setBoxedDoubleValueMap(Collections.singletonMap(key, boxedDoubleValue));
    obj.setBoxedCharValueMap(Collections.singletonMap(key, boxedCharValue));
    obj.setAggregatedDataObjectMap(Collections.singletonMap(key, aggregatedDataObject));
    obj.setBufferMap(Collections.singletonMap(key, buffer));
    obj.setJsonObjectMap(Collections.singletonMap(key, jsonObject));
    obj.setJsonArrayMap(Collections.singletonMap(key, jsonArray));
    obj.setHttpMethodMap(Collections.singletonMap(key, httpMethod));
    obj.setObjectMap(map);


    JsonObject json = new JsonObject();
    TestDataObjectConverter.toJson(obj, json);
    json = new JsonObject(json.encode());

    assertEquals(stringValue, json.getString("stringValue"));
    assertEquals(booleanValue, json.getBoolean("booleanValue"));
    assertEquals((int)byteValue, (int)json.getInteger("byteValue"));
    assertEquals((int)shortValue, (int)json.getInteger("shortValue"));
    assertEquals(intValue, (int)json.getInteger("intValue"));
    assertEquals(longValue, (long)json.getLong("longValue"));
    assertEquals(floatValue, json.getFloat("floatValue"), 0.001);
    assertEquals(doubleValue, (double)json.getFloat("doubleValue"), 0.001);
    assertEquals(Character.toString(charValue), json.getString("charValue"));
    assertEquals(boxedBooleanValue, json.getBoolean("boxedBooleanValue"));
    assertEquals((int)boxedByteValue, (int)json.getInteger("boxedByteValue"));
    assertEquals((int)boxedShortValue, (int)json.getInteger("boxedShortValue"));
    assertEquals(boxedIntValue, json.getInteger("boxedIntValue"));
    assertEquals(boxedLongValue, json.getLong("boxedLongValue"));
    assertEquals(boxedFloatValue, json.getFloat("boxedFloatValue"), 0.001);
    assertEquals(boxedDoubleValue, (double) json.getFloat("boxedDoubleValue"), 0.001);
    assertEquals(Character.toString(boxedCharValue), json.getString("boxedCharValue"));
    assertEquals(aggregatedDataObject.toJson(), json.getJsonObject("aggregatedDataObject"));
    assertEquals(buffer, Buffer.buffer(json.getBinary("buffer")));
    assertEquals(jsonObject, json.getJsonObject("jsonObject"));
    assertEquals(jsonArray, json.getJsonArray("jsonArray"));
    assertEquals(httpMethod.name(), json.getString("httpMethod"));
    assertEquals(new JsonArray().add(stringValue), json.getJsonArray("stringValues"));
    assertEquals(new JsonArray().add(boxedBooleanValue), json.getJsonArray("boxedBooleanValues"));
    assertEquals(new JsonArray().add(boxedByteValue), json.getJsonArray("boxedByteValues"));
    assertEquals(new JsonArray().add(boxedShortValue), json.getJsonArray("boxedShortValues"));
    assertEquals(new JsonArray().add(boxedIntValue), json.getJsonArray("boxedIntValues"));
    assertEquals(new JsonArray().add(boxedLongValue), json.getJsonArray("boxedLongValues"));
    assertEquals(1, json.getJsonArray("boxedFloatValues").size());
    assertEquals(boxedFloatValue, json.getJsonArray("boxedFloatValues").getFloat(0), 0.001);
    assertEquals(1, json.getJsonArray("boxedDoubleValues").size());
    assertEquals(boxedDoubleValue, json.getJsonArray("boxedDoubleValues").getDouble(0), 0.001);
    assertEquals(new JsonArray().add(Character.toString(boxedCharValue)), json.getJsonArray("boxedCharValues"));
    assertEquals(new JsonArray().add(aggregatedDataObject.toJson()), json.getJsonArray("aggregatedDataObjects"));
    assertEquals(new JsonArray().add(Base64.getEncoder().encodeToString(buffer.getBytes())), json.getJsonArray("buffers"));
    assertEquals(new JsonArray().add(jsonObject), json.getJsonArray("jsonObjects"));
    assertEquals(new JsonArray().add(jsonArray), json.getJsonArray("jsonArrays"));
    assertEquals(new JsonArray().add(httpMethod.name()), json.getJsonArray("httpMethods"));
    assertEquals(new JsonArray().add(list.get(0)).add(list.get(1)).add(list.get(2)), json.getJsonArray("objects"));
    assertEquals(new JsonObject().put(key, stringValue), json.getJsonObject("stringValueMap"));
    assertEquals(new JsonObject().put(key, boxedBooleanValue), json.getJsonObject("boxedBooleanValueMap"));
    assertEquals(new JsonObject().put(key, boxedByteValue), json.getJsonObject("boxedByteValueMap"));
    assertEquals(new JsonObject().put(key, boxedShortValue), json.getJsonObject("boxedShortValueMap"));
    assertEquals(new JsonObject().put(key, boxedIntValue), json.getJsonObject("boxedIntValueMap"));
    assertEquals(new JsonObject().put(key, boxedLongValue), json.getJsonObject("boxedLongValueMap"));
    assertEquals(1, json.getJsonObject("boxedFloatValueMap").size());
    assertEquals(boxedFloatValue, json.getJsonObject("boxedFloatValueMap").getFloat(key), 0.001);
    assertEquals(1, json.getJsonObject("boxedDoubleValueMap").size());
    assertEquals(boxedDoubleValue, json.getJsonObject("boxedDoubleValueMap").getDouble(key), 0.001);
    assertEquals(new JsonObject().put(key, Character.toString(boxedCharValue)), json.getJsonObject("boxedCharValueMap"));
    assertEquals(new JsonObject().put(key, aggregatedDataObject.toJson()), json.getJsonObject("aggregatedDataObjectMap"));
    assertEquals(new JsonObject().put(key, Base64.getEncoder().encodeToString(buffer.getBytes())), json.getJsonObject("bufferMap"));
    assertEquals(new JsonObject().put(key, jsonObject), json.getJsonObject("jsonObjectMap"));
    assertEquals(new JsonObject().put(key, jsonArray), json.getJsonObject("jsonArrayMap"));
    assertEquals(new JsonObject().put(key, httpMethod.name()), json.getJsonObject("httpMethodMap"));
    assertEquals(toJson(map), json.getJsonObject("objectMap"));
  }

  @Test
  public void testEmptyDataObjectToJson() {

    TestDataObject obj = new TestDataObject();

    JsonObject json = new JsonObject();
    TestDataObjectConverter.toJson(obj, json);
    json = new JsonObject(json.encode());

    assertEquals(null, json.getString("stringValue"));
    assertEquals(false, json.getBoolean("booleanValue"));
    assertEquals(0, (int)json.getInteger("byteValue"));
    assertEquals(0, (int)json.getInteger("shortValue"));
    assertEquals(0, (int)json.getInteger("intValue"));
    assertEquals(0L, (long) json.getLong("longValue"));
    assertEquals(0f, (float) json.getFloat("floatValue"), 0);
    assertEquals(0d, (double) json.getFloat("doubleValue"), 0);
    assertEquals(Character.toString((char)0), json.getString("charValue"));
    assertEquals(null, json.getBoolean("boxedBooleanValue"));
    assertEquals(null, json.getInteger("boxedByteValue"));
    assertEquals(null, json.getInteger("boxedShortValue"));
    assertEquals(null, json.getInteger("boxedIntValue"));
    assertEquals(null, json.getLong("boxedLongValue"));
    assertEquals(null, json.getFloat("boxedFloatValue"));
    assertEquals(null, json.getFloat("boxedDoubleValue"));
    assertEquals(null, json.getString("boxedCharValue"));
    assertEquals(null, json.getJsonObject("aggregatedDataObject"));
    assertEquals(null, json.getBinary("buffer"));
    assertEquals(null, json.getJsonObject("jsonObject"));
    assertEquals(null, json.getJsonArray("jsonArray"));
    assertEquals(null, json.getString("httpMethod"));
    assertEquals(null, json.getJsonArray("stringValues"));
    assertEquals(null, json.getJsonArray("boxedBooleanValues"));
    assertEquals(null, json.getJsonArray("boxedByteValues"));
    assertEquals(null, json.getJsonArray("boxedShortValues"));
    assertEquals(null, json.getJsonArray("boxedIntValues"));
    assertEquals(null, json.getJsonArray("boxedLongValues"));
    assertEquals(null, json.getJsonArray("boxedFloatValues"));
    assertEquals(null, json.getJsonArray("boxedDoubleValues"));
    assertEquals(null, json.getJsonArray("boxedCharValues"));
    assertEquals(null, json.getJsonArray("aggregatedDataObjects"));
    assertEquals(null, json.getJsonArray("buffers"));
    assertEquals(null, json.getJsonArray("jsonObjects"));
    assertEquals(null, json.getJsonArray("jsonArrays"));
    assertEquals(null, json.getJsonArray("httpMethods"));
    assertEquals(null, json.getJsonArray("objects"));
    assertEquals(new JsonArray(), json.getJsonArray("addedStringValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBooleanValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedByteValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedShortValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedIntValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedLongValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedFloatValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedDoubleValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedCharValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedBooleanValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedByteValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedShortValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedIntValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedLongValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedFloatValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedDoubleValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBoxedCharValues"));
    assertEquals(new JsonArray(), json.getJsonArray("addedAggregatedDataObjects"));
    assertEquals(new JsonArray(), json.getJsonArray("addedBuffers"));
    assertEquals(new JsonArray(), json.getJsonArray("addedJsonObjects"));
    assertEquals(new JsonArray(), json.getJsonArray("addedJsonArrays"));
    assertEquals(new JsonArray(), json.getJsonArray("addedHttpMethods"));
    assertEquals(new JsonArray(), json.getJsonArray("addedObjects"));
    assertEquals(null, json.getJsonArray("stringValueMap"));
    assertEquals(null, json.getJsonArray("boxedBooleanValueMap"));
    assertEquals(null, json.getJsonArray("boxedByteValueMap"));
    assertEquals(null, json.getJsonArray("boxedShortValueMap"));
    assertEquals(null, json.getJsonArray("boxedIntValueMap"));
    assertEquals(null, json.getJsonArray("boxedLongValueMap"));
    assertEquals(null, json.getJsonArray("boxedFloatValueMap"));
    assertEquals(null, json.getJsonArray("boxedDoubleValueMap"));
    assertEquals(null, json.getJsonArray("boxedCharValueMap"));
    assertEquals(null, json.getJsonArray("aggregatedDataObjectMap"));
    assertEquals(null, json.getJsonArray("bufferMap"));
    assertEquals(null, json.getJsonArray("jsonObjectMap"));
    assertEquals(null, json.getJsonArray("jsonArrayMap"));
    assertEquals(null, json.getJsonArray("httpMethodMap"));
    assertEquals(null, json.getJsonArray("objectMap"));
  }

  @Test
  public void testNoConverters() {
    try {
      NoConverterDataObject.class.getClassLoader().loadClass(NoConverterDataObject.class.getName() + "Converter");
      fail("Was not expecting a converter to be generated");
    } catch (ClassNotFoundException ignore) {
      // Ok
    }
  }

  @Test
  public void testInherit() {
    ChildInheritingDataObject obj = new ChildInheritingDataObject();
    JsonObject expectedJson = new JsonObject();
    expectedJson.put("childProperty", "childProperty_value");
    expectedJson.put("parentProperty", "parentProperty_value");
    ChildInheritingDataObjectConverter.fromJson(expectedJson, obj);
    assertEquals("childProperty_value", obj.getChildProperty());
    assertEquals("parentProperty_value", obj.getParentProperty());
    JsonObject json = new JsonObject();
    ChildInheritingDataObjectConverter.toJson(obj, json);
    assertEquals(expectedJson, json);
  }

  @Test
  public void testNotInherit() {
    ChildNotInheritingDataObject obj = new ChildNotInheritingDataObject();
    JsonObject expectedJson = new JsonObject();
    expectedJson.put("childProperty", "childProperty_value");
    expectedJson.put("parentProperty", "parentProperty_value");
    ChildNotInheritingDataObjectConverter.fromJson(expectedJson, obj);
    assertEquals("childProperty_value", obj.getChildProperty());
    assertEquals(null, obj.getParentProperty());
    JsonObject json = new JsonObject();
    ChildNotInheritingDataObjectConverter.toJson(obj, json);
    expectedJson.remove("parentProperty");
    assertEquals(expectedJson, json);
  }
}
