/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.groovy.core.buffer

import java.nio.ByteBuffer

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Buffer extends org.vertx.java.core.buffer.Buffer {

  protected final org.vertx.java.core.buffer.Buffer jBuffer

  private Buffer(org.vertx.java.core.buffer.Buffer jBuffer) {
    this.jBuffer = jBuffer
  }

  Buffer() {
    jBuffer = new org.vertx.java.core.buffer.Buffer();
  }

  Buffer(int initialSizeHint) {
    jBuffer = new org.vertx.java.core.buffer.Buffer(initialSizeHint);
  }

  Buffer(byte[] bytes) {
    jBuffer = new org.vertx.java.core.buffer.Buffer(bytes);
  }

  Buffer(String str, String enc) {
    jBuffer = new org.vertx.java.core.buffer.Buffer(str, enc);
  }

  Buffer(String str) {
    jBuffer = new org.vertx.java.core.buffer.Buffer(str);
  }

  byte getByte(int pos) {
    jBuffer.getByte(pos)
  }

  int getInt(int pos) {
    jBuffer.getInt(pos)
  }

  long getLong(int pos) {
    jBuffer.getLong(pos)
  }

  double getDouble(int pos) {
    jBuffer.getDouble(pos)
  }

  float getFloat(int pos) {
    jBuffer.getFloat(pos)
  }

  short getShort(int pos) {
    jBuffer.getShort(pos)
  }

  byte[] getBytes() {
    jBuffer.getBytes()
  }

  byte[] getBytes(int start, int end) {
    jBuffer.getBytes(start, end)
  }

  Buffer getBuffer(int start, int end) {
    new Buffer(jBuffer.getBuffer(start, end))
  }

  String getString(int start, int end, String enc) {
    jBuffer.getString(start, end, enc)
  }

  String getString(int start, int end) {
    jBuffer.getString(start, end)
  }

  Buffer leftShift(Buffer buff) {
    appendBuffer(buff)
  }

  Buffer appendBuffer(Buffer buff) {
    jBuffer.appendBuffer(buff.jBuffer)
    this
  }

  Buffer leftShift(byte[] bytes) {
    appendBytes(bytes)
  }

  Buffer appendBytes(byte[] bytes) {
    jBuffer.appendBytes(bytes)
    this
  }

  Buffer leftShift(byte b) {
    appendByte(b)
  }

  Buffer appendByte(byte b) {
    jBuffer.appendByte(b)
    this
  }

  Buffer leftShift(int i) {
    appendInt(i)
  }

  Buffer appendInt(int i) {
    jBuffer.appendInt(i)
    this
  }

  Buffer leftShift(long l) {
    appendLong(l)
  }

  Buffer appendLong(long l) {
    jBuffer.appendLong(l)
    this
  }

  Buffer leftShift(short s) {
    appendShort(s)
  }

  Buffer appendShort(short s) {
    jBuffer.appendShort(s)
    this
  }

  Buffer leftShift(float f) {
    appendFloat(f)
  }

  Buffer appendFloat(float f) {
    jBuffer.appendFloat(f)
    this
  }

  Buffer leftShift(double d) {
    appendDouble(d)
  }

  Buffer appendDouble(double d) {
    jBuffer.appendDouble(d)
    this
  }

  Buffer appendString(String str, String enc) {
    jBuffer.appendString(str, enc)
    this
  }

  Buffer leftShift(String s) {
    appendString(s)
  }

  Buffer appendString(String str) {
    jBuffer.appendString(str)
    this
  }

  Buffer setByte(int pos, byte b) {
    jBuffer.setByte(pos, b)
    this
  }

  Buffer setInt(int pos, int i) {
    jBuffer.setInt(pos, i)
    this
  }

  Buffer setLong(int pos, long l) {
    jBuffer.setLong(pos, l)
    this
  }

  Buffer setDouble(int pos, double d) {
    jBuffer.setDouble(pos, d)
    this
  }

  Buffer setFloat(int pos, float f) {
    jBuffer.setFloat(pos, f)
    this
  }

  Buffer setShort(int pos, short s) {
    jBuffer.setShort(pos, s)
    this
  }

  Buffer setBuffer(int pos, org.vertx.java.core.buffer.Buffer b) {
    jBuffer.setBuffer(pos, b)
    this
  }

  Buffer setBytes(int pos, ByteBuffer b) {
    jBuffer.setBytes(pos, b)
    this
  }

  Buffer setBytes(int pos, byte[] b) {
    jBuffer.setBytes(pos, b)
    this
  }

  Buffer setString(int pos, String str) {
    jBuffer.setString(pos, str)
    this
  }

  Buffer setString(int pos, String str, String enc) {
    jBuffer.setString(pos, str, enc)
    this
  }

  int length() {
    jBuffer.length()
  }

  Buffer copy() {
    new Buffer(jBuffer.copy())
  }

}
