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

package io.vertx.test.codegen;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class TestDataObject {

  private String stringValue;
  private boolean booleanValue;
  private byte byteValue;
  private short shortValue;
  private int intValue;
  private long longValue;
  private float floatValue;
  private double doubleValue;
  private char charValue;
  private Boolean boxedBooleanValue;
  private Byte boxedByteValue;
  private Short boxedShortValue;
  private Integer boxedIntValue;
  private Long boxedLongValue;
  private Float boxedFloatValue;
  private Double boxedDoubleValue;
  private Character boxedCharValue;
  private AggregatedDataObject aggregatedDataObject;
  private Buffer buffer;
  private JsonObject jsonObject;
  private JsonArray jsonArray;
  private HttpMethod httpMethod;

  private List<String> stringValues;
  private List<Boolean> boxedBooleanValues;
  private List<Byte> boxedByteValues;
  private List<Short> boxedShortValues;
  private List<Integer> boxedIntValues;
  private List<Long> boxedLongValues;
  private List<Float> boxedFloatValues;
  private List<Double> boxedDoubleValues;
  private List<Character> boxedCharValues;
  private List<AggregatedDataObject> aggregatedDataObjects;
  private List<Buffer> buffers;
  private List<JsonObject> jsonObjects;
  private List<JsonArray> jsonArrays;
  private List<HttpMethod> httpMethods;

  private List<String> addedStringValues = new ArrayList<>();
  private List<Boolean> addedBooleanValues = new ArrayList<>();
  private List<Byte> addedByteValues = new ArrayList<>();
  private List<Short> addedShortValues = new ArrayList<>();
  private List<Integer> addedIntValues = new ArrayList<>();
  private List<Long> addedLongValues = new ArrayList<>();
  private List<Float> addedFloatValues = new ArrayList<>();
  private List<Double> addedDoubleValues = new ArrayList<>();
  private List<Character> addedCharValues = new ArrayList<>();
  private List<Boolean> addedBoxedBooleanValues = new ArrayList<>();
  private List<Byte> addedBoxedByteValues = new ArrayList<>();
  private List<Short> addedBoxedShortValues = new ArrayList<>();
  private List<Integer> addedBoxedIntValues = new ArrayList<>();
  private List<Long> addedBoxedLongValues = new ArrayList<>();
  private List<Float> addedBoxedFloatValues = new ArrayList<>();
  private List<Double> addedBoxedDoubleValues = new ArrayList<>();
  private List<Character> addedBoxedCharValues = new ArrayList<>();
  private List<AggregatedDataObject> addedAggregatedDataObjects = new ArrayList<>();
  private List<Buffer> addedBuffers = new ArrayList<>();
  private List<JsonObject> addedJsonObjects = new ArrayList<>();
  private List<JsonArray> addedJsonArrays = new ArrayList<>();
  private List<HttpMethod> addedHttpMethods = new ArrayList<>();

  public TestDataObject() {
  }

  public TestDataObject(TestDataObject copy) {
  }

  public TestDataObject(JsonObject json) {
  }

  public String getStringValue() {
    return stringValue;
  }

  public TestDataObject setStringValue(String value) {
    this.stringValue = value;
    return this;
  }

  public boolean isBooleanValue() {
    return booleanValue;
  }

  public TestDataObject setBooleanValue(boolean value) {
    this.booleanValue = value;
    return this;
  }

  public byte getByteValue() {
    return byteValue;
  }

  public TestDataObject setByteValue(byte byteValue) {
    this.byteValue = byteValue;
    return this;
  }

  public short getShortValue() {
    return shortValue;
  }

  public TestDataObject setShortValue(short shortValue) {
    this.shortValue = shortValue;
    return this;
  }

  public int getIntValue() {
    return intValue;
  }

  public TestDataObject setIntValue(int intValue) {
    this.intValue = intValue;
    return this;
  }

  public long getLongValue() {
    return longValue;
  }

  public TestDataObject setLongValue(long longValue) {
    this.longValue = longValue;
    return this;
  }

  public float getFloatValue() {
    return floatValue;
  }

  public TestDataObject setFloatValue(float floatValue) {
    this.floatValue = floatValue;
    return this;
  }

  public double getDoubleValue() {
    return doubleValue;
  }

  public TestDataObject setDoubleValue(double doubleValue) {
    this.doubleValue = doubleValue;
    return this;
  }

  public char getCharValue() {
    return charValue;
  }

  public TestDataObject setCharValue(char charValue) {
    this.charValue = charValue;
    return this;
  }

  public Boolean isBoxedBooleanValue() {
    return boxedBooleanValue;
  }

  public TestDataObject setBoxedBooleanValue(Boolean value) {
    this.boxedBooleanValue = value;
    return this;
  }

  public Byte getBoxedByteValue() {
    return boxedByteValue;
  }

  public TestDataObject setBoxedByteValue(Byte boxedByteValue) {
    this.boxedByteValue = boxedByteValue;
    return this;
  }

  public Short getBoxedShortValue() {
    return boxedShortValue;
  }

  public TestDataObject setBoxedShortValue(Short boxedShortValue) {
    this.boxedShortValue = boxedShortValue;
    return this;
  }

  public Integer getBoxedIntValue() {
    return boxedIntValue;
  }

  public TestDataObject setBoxedIntValue(Integer boxedIntValue) {
    this.boxedIntValue = boxedIntValue;
    return this;
  }

  public Long getBoxedLongValue() {
    return boxedLongValue;
  }

  public TestDataObject setBoxedLongValue(Long boxedLongValue) {
    this.boxedLongValue = boxedLongValue;
    return this;
  }

  public Float getBoxedFloatValue() {
    return boxedFloatValue;
  }

  public TestDataObject setBoxedFloatValue(Float boxedFloatValue) {
    this.boxedFloatValue = boxedFloatValue;
    return this;
  }

  public Double getBoxedDoubleValue() {
    return boxedDoubleValue;
  }

  public TestDataObject setBoxedDoubleValue(Double boxedDoubleValue) {
    this.boxedDoubleValue = boxedDoubleValue;
    return this;
  }

  public Character getBoxedCharValue() {
    return boxedCharValue;
  }

  public TestDataObject setBoxedCharValue(Character boxedCharValue) {
    this.boxedCharValue = boxedCharValue;
    return this;
  }

  public AggregatedDataObject getAggregatedDataObject() {
    return aggregatedDataObject;
  }

  public TestDataObject setAggregatedDataObject(AggregatedDataObject aggregatedDataObject) {
    this.aggregatedDataObject = aggregatedDataObject;
    return this;
  }

  public Buffer getBuffer() {
    return buffer;
  }

  public TestDataObject setBuffer(Buffer buffer) {
    this.buffer = buffer;
    return this;
  }

  public JsonObject getJsonObject() {
    return jsonObject;
  }

  public TestDataObject setJsonObject(JsonObject jsonObject) {
    this.jsonObject = jsonObject;
    return this;
  }

  public JsonArray getJsonArray() {
    return jsonArray;
  }

  public TestDataObject setJsonArray(JsonArray jsonArray) {
    this.jsonArray = jsonArray;
    return this;
  }

  public HttpMethod getHttpMethod() {
    return httpMethod;
  }

  public TestDataObject setHttpMethod(HttpMethod httpMethod) {
    this.httpMethod = httpMethod;
    return this;
  }

  public List<String> getStringValues() {
    return stringValues;
  }

  public TestDataObject setStringValues(List<String> stringValues) {
    this.stringValues = stringValues;
    return this;
  }

  public List<Boolean> getBoxedBooleanValues() {
    return boxedBooleanValues;
  }

  public TestDataObject setBoxedBooleanValues(List<Boolean> boxedBooleanValues) {
    this.boxedBooleanValues = boxedBooleanValues;
    return this;
  }

  public List<Byte> getBoxedByteValues() {
    return boxedByteValues;
  }

  public TestDataObject setBoxedByteValues(List<Byte> boxedByteValues) {
    this.boxedByteValues = boxedByteValues;
    return this;
  }

  public List<Short> getBoxedShortValues() {
    return boxedShortValues;
  }

  public TestDataObject setBoxedShortValues(List<Short> boxedShortValues) {
    this.boxedShortValues = boxedShortValues;
    return this;
  }

  public List<Integer> getBoxedIntValues() {
    return boxedIntValues;
  }

  public TestDataObject setBoxedIntValues(List<Integer> boxedIntValues) {
    this.boxedIntValues = boxedIntValues;
    return this;
  }

  public List<Long> getBoxedLongValues() {
    return boxedLongValues;
  }

  public TestDataObject setBoxedLongValues(List<Long> boxedLongValues) {
    this.boxedLongValues = boxedLongValues;
    return this;
  }

  public List<Float> getBoxedFloatValues() {
    return boxedFloatValues;
  }

  public TestDataObject setBoxedFloatValues(List<Float> boxedFloatValues) {
    this.boxedFloatValues = boxedFloatValues;
    return this;
  }

  public List<Double> getBoxedDoubleValues() {
    return boxedDoubleValues;
  }

  public TestDataObject setBoxedDoubleValues(List<Double> boxedDoubleValues) {
    this.boxedDoubleValues = boxedDoubleValues;
    return this;
  }

  public List<Character> getBoxedCharValues() {
    return boxedCharValues;
  }

  public TestDataObject setBoxedCharValues(List<Character> boxedCharValues) {
    this.boxedCharValues = boxedCharValues;
    return this;
  }

  public List<AggregatedDataObject> getAggregatedDataObjects() {
    return aggregatedDataObjects;
  }

  public TestDataObject setAggregatedDataObjects(List<AggregatedDataObject> aggregatedDataObjects) {
    this.aggregatedDataObjects = aggregatedDataObjects;
    return this;
  }

  public List<Buffer> getBuffers() {
    return buffers;
  }

  public TestDataObject setBuffers(List<Buffer> buffers) {
    this.buffers = buffers;
    return this;
  }

  public List<JsonObject> getJsonObjects() {
    return jsonObjects;
  }

  public TestDataObject setJsonObjects(List<JsonObject> jsonObjects) {
    this.jsonObjects = jsonObjects;
    return this;
  }

  public List<JsonArray> getJsonArrays() {
    return jsonArrays;
  }

  public TestDataObject setJsonArrays(List<JsonArray> jsonArrays) {
    this.jsonArrays = jsonArrays;
    return this;
  }

  public List<HttpMethod> getHttpMethods() {
    return httpMethods;
  }

  public TestDataObject setHttpMethods(List<HttpMethod> httpMethods) {
    this.httpMethods = httpMethods;
    return this;
  }

  public List<String> getAddedStringValues() {
    return addedStringValues;
  }

  public TestDataObject addAddedStringValue(String addedStringValue) {
    this.addedStringValues.add(addedStringValue);
    return this;
  }

  public List<Boolean> getAddedBooleanValues() {
    return addedBooleanValues;
  }

  public TestDataObject addAddedBooleanValue(boolean addedBoxedBooleanValue) {
    this.addedBooleanValues.add(addedBoxedBooleanValue);
    return this;
  }

  public List<Byte> getAddedByteValues() {
    return addedByteValues;
  }

  public TestDataObject addAddedByteValue(byte addedBoxedByteValue) {
    this.addedByteValues.add(addedBoxedByteValue);
    return this;
  }

  public List<Short> getAddedShortValues() {
    return addedShortValues;
  }

  public TestDataObject addAddedShortValue(short addedBoxedShortValue) {
    this.addedShortValues.add(addedBoxedShortValue);
    return this;
  }

  public List<Integer> getAddedIntValues() {
    return addedIntValues;
  }

  public TestDataObject addAddedIntValue(int addedBoxedIntValue) {
    this.addedIntValues.add(addedBoxedIntValue);
    return this;
  }

  public List<Long> getAddedLongValues() {
    return addedLongValues;
  }

  public TestDataObject addAddedLongValue(long addedBoxedLongValue) {
    this.addedLongValues.add(addedBoxedLongValue);
    return this;
  }

  public List<Float> getAddedFloatValues() {
    return addedFloatValues;
  }

  public TestDataObject addAddedFloatValue(float addedBoxedFloatValue) {
    this.addedFloatValues.add(addedBoxedFloatValue);
    return this;
  }

  public List<Double> getAddedDoubleValues() {
    return addedDoubleValues;
  }

  public TestDataObject addAddedDoubleValue(double addedBoxedDoubleValue) {
    this.addedDoubleValues.add(addedBoxedDoubleValue);
    return this;
  }

  public List<Character> getAddedCharValues() {
    return addedCharValues;
  }

  public TestDataObject addAddedCharValue(char addedBoxedCharValue) {
    this.addedCharValues.add(addedBoxedCharValue);
    return this;
  }

  public List<Boolean> getAddedBoxedBooleanValues() {
    return addedBoxedBooleanValues;
  }

  public TestDataObject addAddedBoxedBooleanValue(Boolean addedBoxedBooleanValue) {
    this.addedBoxedBooleanValues.add(addedBoxedBooleanValue);
    return this;
  }

  public List<Byte> getAddedBoxedByteValues() {
    return addedBoxedByteValues;
  }

  public TestDataObject addAddedBoxedByteValue(Byte addedBoxedByteValue) {
    this.addedBoxedByteValues.add(addedBoxedByteValue);
    return this;
  }

  public List<Short> getAddedBoxedShortValues() {
    return addedBoxedShortValues;
  }

  public TestDataObject addAddedBoxedShortValue(Short addedBoxedShortValue) {
    this.addedBoxedShortValues.add(addedBoxedShortValue);
    return this;
  }

  public List<Integer> getAddedBoxedIntValues() {
    return addedBoxedIntValues;
  }

  public TestDataObject addAddedBoxedIntValue(Integer addedBoxedIntValue) {
    this.addedBoxedIntValues.add(addedBoxedIntValue);
    return this;
  }

  public List<Long> getAddedBoxedLongValues() {
    return addedBoxedLongValues;
  }

  public TestDataObject addAddedBoxedLongValue(Long addedBoxedLongValue) {
    this.addedBoxedLongValues.add(addedBoxedLongValue);
    return this;
  }

  public List<Float> getAddedBoxedFloatValues() {
    return addedBoxedFloatValues;
  }

  public TestDataObject addAddedBoxedFloatValue(Float addedBoxedFloatValue) {
    this.addedBoxedFloatValues.add(addedBoxedFloatValue);
    return this;
  }

  public List<Double> getAddedBoxedDoubleValues() {
    return addedBoxedDoubleValues;
  }

  public TestDataObject addAddedBoxedDoubleValue(Double addedBoxedDoubleValue) {
    this.addedBoxedDoubleValues.add(addedBoxedDoubleValue);
    return this;
  }

  public List<Character> getAddedBoxedCharValues() {
    return addedBoxedCharValues;
  }

  public TestDataObject addAddedBoxedCharValue(Character addedBoxedCharValue) {
    this.addedBoxedCharValues.add(addedBoxedCharValue);
    return this;
  }

  public List<AggregatedDataObject> getAddedAggregatedDataObjects() {
    return addedAggregatedDataObjects;
  }

  public TestDataObject addAddedAggregatedDataObject(AggregatedDataObject addedAggregatedDataObject) {
    this.addedAggregatedDataObjects.add(addedAggregatedDataObject);
    return this;
  }

  public List<Buffer> getAddedBuffers() {
    return addedBuffers;
  }

  public TestDataObject addAddedBuffer(Buffer addedBuffer) {
    this.addedBuffers.add(addedBuffer);
    return this;
  }

  public List<JsonObject> getAddedJsonObjects() {
    return addedJsonObjects;
  }

  public TestDataObject addAddedJsonObject(JsonObject addedJsonObject) {
    this.addedJsonObjects.add(addedJsonObject);
    return this;
  }

  public List<JsonArray> getAddedJsonArrays() {
    return addedJsonArrays;
  }

  public TestDataObject addAddedJsonArray(JsonArray addedJsonArray) {
    this.addedJsonArrays.add(addedJsonArray);
    return this;
  }

  public List<HttpMethod> getAddedHttpMethods() {
    return addedHttpMethods;
  }

  public TestDataObject addAddedHttpMethod(HttpMethod addedHttpMethod) {
    this.addedHttpMethods.add(addedHttpMethod);
    return this;
  }
}
