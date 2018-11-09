/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
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
@DataObject(generateConverter=true, publicConverter=false)
public class StreamPriority {
    
    public static final StreamPriority DEFAULT = new StreamPriority();
    /**
     * 
     */
    @DataObjectProperty()
    private short weight;
    private int streamDependency;
    private boolean exclusive;
    
    private StreamPriority() {
        this(0, Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, false);
    }
    
    public StreamPriority(JsonObject json) {
        this.weight = json.getInteger("weight", (int)Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT).shortValue();
        this.streamDependency = json.getInteger("streamDependency", 0);
        this.exclusive = json.getBoolean("exclusive", false);
     }

    

    public StreamPriority(int streamDependency, short weight, boolean exclusive) {
        super();
        this.weight = weight;
        this.streamDependency = streamDependency;
        this.exclusive = exclusive;
    }
    

    public StreamPriority(int streamDependency, short weight) {
        this(streamDependency, weight, false);
    }

    public StreamPriority(short weight) {
        this(0, weight, false);
    }


    public StreamPriority(StreamPriority other) {
        this.weight = other.weight;
        this.streamDependency = other.streamDependency;
        this.exclusive = other.exclusive;
    }

    public short getWeight() {
        return weight;
    }
    
    public int getStreamDependency() {
        return streamDependency;
    }
    
    public boolean isExclusive() {
        return exclusive;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (exclusive ? 1231 : 1237);
        result = prime * result + streamDependency;
        result = prime * result + weight;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        StreamPriority other = (StreamPriority) obj;
        if (exclusive != other.exclusive)
            return false;
        if (streamDependency != other.streamDependency)
            return false;
        if (weight != other.weight)
            return false;
        return true;
    }
    
    public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.put("weight", weight);
      json.put("streamDependency", streamDependency);
      json.put("exclusive", exclusive);
      return json;
    }

    @Override
    public String toString() {
      return "StreamPriority [weight=" + weight + ", streamDependency=" + streamDependency + ", exclusive=" + exclusive + "]";
    }

    
    
}
