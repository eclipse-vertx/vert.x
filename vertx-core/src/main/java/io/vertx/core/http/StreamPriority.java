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

package io.vertx.core.http;

import io.netty.handler.codec.http2.Http2CodecUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * This class represents HTTP/2 stream priority defined in RFC 7540 clause 5.3
 */
@DataObject
public class StreamPriority {

  public static final int DEFAULT_DEPENDENCY = 0;
  public static final short DEFAULT_WEIGHT = Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
  public static final boolean DEFAULT_EXCLUSIVE = false;
  public static final int DEFAULT_HTTP3_URGENCY = 0;
  public static final boolean DEFAULT_HTTP3_INCREMENTAL = false;

  private short weight;
  private int dependency;
  private boolean exclusive;
  private int http3Urgency;
  private boolean http3Incremental;

  public StreamPriority() {
    weight = DEFAULT_WEIGHT;
    dependency = DEFAULT_DEPENDENCY;
    exclusive = DEFAULT_EXCLUSIVE;
    http3Urgency = DEFAULT_HTTP3_URGENCY;
    http3Incremental = DEFAULT_HTTP3_INCREMENTAL;
  }

  public StreamPriority(JsonObject json) {
    this.weight = json.getInteger("weight", (int)DEFAULT_WEIGHT).shortValue();
    this.dependency = json.getInteger("dependency", DEFAULT_DEPENDENCY);
    this.exclusive = json.getBoolean("exclusive", DEFAULT_EXCLUSIVE);
    this.http3Urgency = json.getInteger("http3Urgency", DEFAULT_HTTP3_URGENCY);
    this.http3Incremental = json.getBoolean("http3Incremental", DEFAULT_HTTP3_INCREMENTAL);
  }

  public StreamPriority(StreamPriority other) {
    this.weight = other.weight;
    this.dependency = other.dependency;
    this.exclusive = other.exclusive;
    this.http3Urgency = other.http3Urgency;
    this.http3Incremental = other.http3Incremental;
  }

  /**
   * @return An integer value between {@code 1} and {@code 256} representing a priority weight
   *         for the stream.
   */
  public short getWeight() {
    return weight;
  }

  /**
   * Set the priority weight.
   *
   * @param weight the new value
   * @return a reference to this, so the API can be used fluently
   */
  public StreamPriority setWeight(short weight) {
    this.weight = weight;
    return this;
  }

  /**
   * @return A stream identifier for the stream that this stream depends on.
   */
  public int getDependency() {
    return dependency;
  }

  /**
   * Set the priority dependency value.
   *
   * @param dependency the new value
   * @return a reference to this, so the API can be used fluently
   */
  public StreamPriority setDependency(int dependency) {
    this.dependency = dependency;
    return this;
  }

  /**
   * @return A flag indicating that the stream dependency is exclusive.
   */
  public boolean isExclusive() {
    return exclusive;
  }

  /**
   * Set the priority exclusive value.
   *
   * @param exclusive the new value
   * @return a reference to this, so the API can be used fluently
   */
  public StreamPriority setExclusive(boolean exclusive) {
    this.exclusive = exclusive;
    return this;
  }

  /**
   * @return A stream identifier for the stream that this stream depends on.
   */
  public int getHttp3Urgency() {
    return http3Urgency;
  }

  /**
   * Set the http3 priority urgency value.
   *
   * @param http3Urgency the new value
   * @return a reference to this, so the API can be used fluently
   */
  public StreamPriority setHttp3Urgency(int http3Urgency) {
    this.http3Urgency = http3Urgency;
    return this;
  }

  /**
   * @return A flag indicating that the stream is incremental.
   */
  public boolean isHttp3Incremental() {
    return http3Incremental;
  }

  /**
   * Set the http3 priority incremental value.
   *
   * @param http3Incremental the new value
   * @return a reference to this, so the API can be used fluently
   */
  public StreamPriority setHttp3Incremental(boolean http3Incremental) {
    this.http3Incremental = http3Incremental;
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (exclusive ? 1231 : 1237);
    result = prime * result + dependency;
    result = prime * result + weight;
    result = prime * result + http3Urgency;
    result = prime * result + (http3Incremental ? 1231 : 1237);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;

    StreamPriority other = (StreamPriority) obj;
    if (exclusive != other.exclusive) return false;
    if (dependency != other.dependency) return false;
    if (weight != other.weight) return false;
    if (http3Incremental != other.http3Incremental) return false;
    if (http3Urgency != other.http3Urgency) return false;

    return true;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("weight", weight);
    json.put("dependency", dependency);
    json.put("exclusive", exclusive);
    json.put("http3Urgency", http3Urgency);
    json.put("http3Incremental", http3Incremental);
    return json;
  }

  @Override
  public String toString() {
    return "StreamPriority [weight=" + weight + ", dependency=" + dependency + ", exclusive=" + exclusive + ", http3Incremental=" + http3Incremental + ", http3Urgency=" + http3Urgency + "]";
  }

}
