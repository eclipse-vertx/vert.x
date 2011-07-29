/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.core.parsetools;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;

public class RecordParser implements DataHandler {

  private Buffer buff;
  private int pos;            // Current position in buffer
  private int start;          // Position of beginning of current record
  private int delimPos;       // Position of current match in delimeter array
  private boolean reset;      // Allows user to toggle mode / change delim when records are emitted

  private boolean delimited;
  private byte[] delim;
  private int recordSize;
  private final DataHandler output;

  private RecordParser(DataHandler output) {
    this.output = output;
  }

  /**
   * Helper method to convert a latin-1 String to an array of bytes for use as a delimiter
   * Please do not use this for non latin-1 characters
   *
   * @param str
   * @return The byte[] form of the string
   */
  public static byte[] latin1StringToBytes(String str) {
    byte[] bytes = new byte[str.length()];
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      bytes[i] = (byte) (c & 0xFF);
    }
    return bytes;
  }

  public static RecordParser newDelimited(String delim, DataHandler output) {
    return newDelimited(latin1StringToBytes(delim), output);
  }

  public static RecordParser newDelimited(byte[] delim, DataHandler output) {
    RecordParser ls = new RecordParser(output);
    ls.delimitedMode(delim);
    return ls;
  }

  public static RecordParser newFixed(int size, DataHandler output) {
    RecordParser ls = new RecordParser(output);
    ls.fixedSizeMode(size);
    return ls;
  }

  public void delimitedMode(String delim) {
    delimitedMode(latin1StringToBytes(delim));
  }

  public void delimitedMode(byte[] delim) {
    delimited = true;
    this.delim = delim;
    delimPos = 0;
    reset = true;
  }

  public void fixedSizeMode(int size) {
    delimited = false;
    recordSize = size;
    reset = true;
  }

  private void handleParsing() {
    int len = buff.length();
    do {
      reset = false;
      if (delimited) {
        parseDelimited();
      } else {
        parseFixed();
      }
    } while (reset);

    if (start == len) {
      //Nothing left
      buff = null;
      pos = 0;
    } else {
      buff = buff.copy(start, len);
      pos = buff.length();
    }
    start = 0;
  }

  private void parseDelimited() {
    int len = buff.length();
    for (; pos < len && !reset; pos++) {
      if (buff.byteAt(pos) == delim[delimPos]) {
        delimPos++;
        if (delimPos == delim.length) {
          Buffer ret = buff.slice(start, pos - delim.length + 1);
          start = pos + 1;
          delimPos = 0;
          output.onData(ret);
        }
      }
    }
  }

  private void parseFixed() {
    int len = buff.length();
    while (len - start >= recordSize && !reset) {
      int end = start + recordSize;
      Buffer ret = buff.slice(start, end);
      start = end;
      output.onData(ret);
    }
  }

  public void onData(Buffer buffer) {
    if (buff == null) {
      buff = Buffer.newDynamic(buffer.length());
    }
    buff.append(buffer);
    handleParsing();
  }
}
