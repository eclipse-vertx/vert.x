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

package org.vertx.java.addons.redis;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;
import org.vertx.java.core.parsetools.RecordParser;

/**
 * <p>Parser for Redis replies</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReplyParser implements Handler<Buffer> {

  private static final Logger log = LoggerFactory.getLogger(ReplyParser.class);

  static final byte[] CRLF = new byte[]{'\r', '\n'};
  static final byte STAR = (byte)'*';
  static final byte DOLLAR = (byte)'$';
  static final byte COLON = (byte)':';
  static final byte PLUS = (byte)'+';
  static final byte MINUS = (byte)'-';

  private final RecordParser recordParser;
  private final Handler<RedisReply> replyHandler;
  private ReplyParseState respState = ReplyParseState.TYPE;
  private Buffer[] multiBulkResponses;
  private int multiBulkIndex;

  public ReplyParser(Handler<RedisReply> replyHandler) {
    recordParser = RecordParser.newFixed(1, new Handler<Buffer>() {
      public void handle(Buffer data) {
        doHandle(data);
      }
    });
    this.replyHandler = replyHandler;
  }

  public void handle(Buffer data) {
    recordParser.handle(data);
  }

  private void doHandle(Buffer data) {
    switch (respState) {
      case TYPE:
        byte type = data.getByte(0);
        switch (type) {
          case PLUS:
            respState = ReplyParseState.ONE_LINE;
            recordParser.delimitedMode(CRLF);
            break;
          case MINUS:
            respState = ReplyParseState.ERROR;
            recordParser.delimitedMode(CRLF);
            break;
          case COLON:
            respState = ReplyParseState.INTEGER;
            recordParser.delimitedMode(CRLF);
            break;
          case DOLLAR:
            respState = ReplyParseState.BULK_NUM_BYTES;
            recordParser.delimitedMode(CRLF);
            break;
          case STAR:
            respState = ReplyParseState.MULTI_BULK_COUNT;
            recordParser.delimitedMode(CRLF);
            break;
          default:
            log.error("Invalid response type: " + type);
        }
        break;
      case ONE_LINE:
        sendOneLineReply(data.toString());
        break;
      case ERROR:
        sendErrorReply(data.toString());
        break;
      case INTEGER:
        sendIntegerReply(Integer.valueOf(data.toString()));
        break;
      case BULK_NUM_BYTES:
        String sbytes = data.toString();
        int numBytes = Integer.valueOf(sbytes);
        if (numBytes != -1) {
          respState = ReplyParseState.BULK_DATA;
          recordParser.fixedSizeMode(numBytes + 2); // We add two since this is terminated by a CRLF
        } else {
          if (multiBulkResponses != null) {
            multiBulkIndex++;
            if (multiBulkIndex == multiBulkResponses.length) {
              //Done multi-bulk
              sendMultiBulkReply(multiBulkResponses);
            }
          } else {
            sendBulkReply(null);
          }
        }
        break;
      case BULK_DATA:
        //Remove the trailing CRLF
        Buffer bytes = data.copy(0, data.length() - 2);
        if (multiBulkResponses == null) {
          sendBulkReply(bytes);
        } else {
          multiBulkResponses[multiBulkIndex++] = bytes;
          if (multiBulkIndex == multiBulkResponses.length) {
            //Done multi-bulk
            sendMultiBulkReply(multiBulkResponses);
          } else {
            recordParser.fixedSizeMode(1);
            respState = ReplyParseState.TYPE;
          }
        }
        break;
      case MULTI_BULK_COUNT:
        sbytes = data.toString();
        int numResponses = Integer.valueOf(sbytes);
        if (numResponses != 0) {
          multiBulkResponses = new Buffer[numResponses];
          recordParser.fixedSizeMode(1);
          respState = ReplyParseState.TYPE;
        } else {
          sendMultiBulkReply(new Buffer[0]);
        }
        break;
      default:
        System.err.println("Unknown state");
    }
  }

  private void reset() {
    recordParser.fixedSizeMode(1);
    respState = ReplyParseState.TYPE;
    multiBulkResponses = null;
    multiBulkIndex = 0;
  }

  private void sendOneLineReply(String reply) {
    replyHandler.handle(new RedisReply(reply));
    reset();
  }

  private void sendIntegerReply(int reply) {
    replyHandler.handle(new RedisReply(reply));
    reset();
  }

  private void sendBulkReply(Buffer reply) {
    replyHandler.handle(new RedisReply(reply));
    reset();
  }

  private void sendMultiBulkReply(Buffer[] reply) {
    replyHandler.handle(new RedisReply(reply));
    reset();
  }

  private void sendErrorReply(String error) {
    replyHandler.handle(new RedisReply(RedisReply.Type.ERROR, error));
    reset();
  }

  private enum ReplyParseState {
    TYPE, ONE_LINE, ERROR, INTEGER, BULK_NUM_BYTES, BULK_DATA, MULTI_BULK_COUNT
  }

}
