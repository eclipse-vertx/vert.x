/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.addons.redis;

import org.nodex.java.core.buffer.Buffer;

/**
 * <p>Represents a reply from a Redis server</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedisReply {

  public static enum Type {
    ERROR, ONE_LINE, INTEGER, BULK, MULTI_BULK;
  }

  public final Type type;

  public String line;
  public String error;
  public int intResult;
  public Buffer bulkResult;
  public Buffer[] multiBulkResult;

  public RedisReply(String line) {
    type = Type.ONE_LINE;
    this.line = line;
  }

  public RedisReply(Type type, String line) {
    this.type = type;
    if (this.type == Type.ERROR) {
      this.error = line;
    } else {
      this.line = line;
    }
  }

  public RedisReply(int i) {
    this.type = Type.INTEGER;;
    this.intResult = i;
  }

  public RedisReply(Buffer bulk) {
    this.type = Type.BULK;
    this.bulkResult = bulk;
  }

  public RedisReply(Buffer[] multiBulk) {
    this.type = Type.MULTI_BULK;
    this.multiBulkResult = multiBulk;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder(type.toString()).append(":");
    switch (type) {
      case ERROR: {
        sb.append(error);
        break;
      }
      case ONE_LINE: {
        sb.append(line);
        break;
      }
      case INTEGER:
        sb.append(intResult);
        break;
      case BULK:
        sb.append(bulkResult == null ? "null" : bulkResult.toString());
        break;
      case MULTI_BULK:
        sb.append(multiBulkResult.length).append(":");
        for (Buffer buff: multiBulkResult) {
          sb.append(buff == null ? "null" : buff.toString());
          sb.append("\n");
        }
        break;
    }
    return sb.toString();
  }

}
