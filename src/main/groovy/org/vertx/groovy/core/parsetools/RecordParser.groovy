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

package org.vertx.groovy.core.parsetools

import org.vertx.java.core.Handler
import org.vertx.groovy.core.buffer.Buffer
import org.vertx.java.core.parsetools.RecordParser as JRecordParser

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class RecordParser {

  private final JRecordParser jParser

  private RecordParser(JRecordParser jParser) {
    this.jParser = jParser
  }

  /**
   * Helper method to convert a latin-1 String to an array of bytes for use as a delimiter
   * Please do not use this for non latin-1 characters
   *
   * @param str
   * @return The byte[] form of the string
   */
  public static byte[] latin1StringToBytes(String str) {
    return JRecordParser.latin1StringToBytes(str)
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the String {@code} delim endcoded in latin-1 . Don't use this if your String contains other than latin-1 characters.<p>
   * {@code output} Will receive whole records which have been parsed.
   */
  public static RecordParser newDelimited(String delim, Closure output) {
    return new RecordParser(JRecordParser.newDelimited(delim, {output(new Buffer(it))} as Handler))
  }

  /**
   * Create a new {@code RecordParser} instance, initially in delimited mode, and where the delimiter can be represented
   * by the {@code byte[]} delim.<p>
   * {@code output} Will receive whole records which have been parsed.
   */
  public static RecordParser newDelimited(byte[] delim, Closure output) {
    return new RecordParser(JRecordParser.newDelimited(delim, {output(new Buffer(it))} as Handler))
  }

  /**
   * Create a new {@code RecordParser} instance, initially in fixed size mode, and where the record size is specified
   * by the {@code size} parameter.<p>
   * {@code output} Will receive whole records which have been parsed.
   */
  public static RecordParser newFixed(int size, Closure output) {
    return new RecordParser(JRecordParser.newFixed(size, {output(new Buffer(it))} as Handler))
  }

  /**
   * Flip the parser into delimited mode, and where the delimiter can be represented
   * by the String {@code delim} endcoded in latin-1 . Don't use this if your String contains other than latin-1 characters.<p>
   * This method can be called multiple times with different values of delim while data is being parsed.
   */
  public void delimitedMode(String delim) {
    jParser.delimitedMode(delim)
  }

  /**
   * Flip the parser into delimited mode, and where the delimiter can be represented
   * by the delimiter {@code delim}.<p>
   * This method can be called multiple times with different values of delim while data is being parsed.
   */
  public void delimitedMode(byte[] delim) {
    jParser.delimitedMode(delim)
  }

  /**
   * Flip the parser into fixed size mode, where the record size is specified by {@code size} in bytes.<p>
   * This method can be called multiple times with different values of size while data is being parsed.
   */
  public void fixedSizeMode(int size) {
    jParser.fixedSizeMode(size)
  }

  /**
   * Convert to a closure so it can be plugged into data handlers
   * @return a Closure
   */
  public Closure toClosure() {
    return {jParser.handle(it.toJavaBuffer())}
  }

  public void setOutput(Closure output) {
    jParser.setOutput({output(new Buffer(it))} as Handler)
  }

  public void handle(Buffer data) {
    jParser.handle(new Buffer(data))
  }
}
