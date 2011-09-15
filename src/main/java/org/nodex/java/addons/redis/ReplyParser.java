/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.addons.redis;

import org.nodex.java.core.Completion;
import org.nodex.java.core.Handler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.parsetools.RecordParser;

/**
 * <p>Parser for Redis replies</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReplyParser implements Handler<Buffer> {

  static final byte[] CRLF = new byte[]{'\r', '\n'};
  static final byte STAR = (byte)'*';
  static final byte DOLLAR = (byte)'$';
  static final byte COLON = (byte)':';
  static final byte PLUS = (byte)'+';
  static final byte MINUS = (byte)'-';

  private final RecordParser recordParser;
  private final Handler<Completion<Object>> replyHandler;
  private ReplyParseState respState = ReplyParseState.TYPE;
  private byte[][] multiBulkResponses;
  private int multiBulkIndex;

  public ReplyParser(Handler<Completion<Object>> replyHandler) {
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
            System.err.println("Invalid response type: " + type);
        }
        break;
      case ONE_LINE:
        sendReply(data.toString());
        break;
      case ERROR:
        sendError(data.toString());
        break;
      case INTEGER:
        sendReply(Integer.valueOf(data.toString()));
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
            if (multiBulkIndex > multiBulkResponses.length) {
              //Done multi-bulk
              sendReply(multiBulkResponses);
            }
          } else {
            sendReply(null);
          }
        }
        break;
      case BULK_DATA:
        byte[] b = data.getBytes();
        //Remove the trailing CRLF
        byte[] bytes = new byte[b.length - 2];
        System.arraycopy(b, 0, bytes, 0, bytes.length);
        if (multiBulkResponses == null) {
          sendReply(bytes);
        } else {
          multiBulkResponses[multiBulkIndex++] = bytes;
          if (multiBulkIndex == multiBulkResponses.length) {
            //Done multi-bulk
            sendReply(multiBulkResponses);
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
          multiBulkResponses = new byte[numResponses][];
          recordParser.fixedSizeMode(1);
          respState = ReplyParseState.TYPE;
        } else {
          sendReply(new byte[0][]);
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

  private void sendReply(Object reply) {
    replyHandler.handle(new Completion<>(reply));
    reset();
  }

  private void sendError(String error) {
    replyHandler.handle(new Completion<>(new RedisException(error)));
    reset();
  }

  private enum ReplyParseState {
    TYPE, ONE_LINE, ERROR, INTEGER, BULK_NUM_BYTES, BULK_DATA, MULTI_BULK_COUNT
  }

}
