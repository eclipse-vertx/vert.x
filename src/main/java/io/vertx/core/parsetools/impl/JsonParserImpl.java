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

package io.vertx.core.parsetools.impl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.base.ParserBase;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.async.NonBlockingJsonParser;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.parsetools.JsonEvent;
import io.vertx.core.parsetools.JsonEventType;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.streams.ReadStream;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonParserImpl implements JsonParser {

  private NonBlockingJsonParser parser;
  private JsonToken currentToken;
  private Handler<JsonToken> tokenHandler = this::handleToken;
  private Handler<JsonEvent> eventHandler;
  private BufferingHandler arrayHandler;
  private BufferingHandler objectHandler;
  private Handler<Throwable> exceptionHandler;
  private String currentField;
  private Handler<Void> endHandler;
  private long demand = Long.MAX_VALUE;
  private boolean ended;
  private final ReadStream<Buffer> stream;

  public JsonParserImpl(ReadStream<Buffer> stream) {
    this.stream = stream;
    JsonFactory factory = new JsonFactory();
    try {
      parser = (NonBlockingJsonParser) factory.createNonBlockingByteArrayParser();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  @Override
  public JsonParser pause() {
    demand = 0L;
    return this;
  }

  @Override
  public JsonParser resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public JsonParser fetch(long amount) {
    Arguments.require(amount > 0L, "Fetch amount must be > 0L");
    demand += amount;
    if (demand < 0L) {
      demand = Long.MAX_VALUE;
    }
    checkPending();
    return this;
  }

  @Override
  public JsonParser endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public JsonParser handler(Handler<JsonEvent> handler) {
    eventHandler = handler;
    if (stream != null) {
      if (handler != null) {
        stream.endHandler(v -> end());
        stream.exceptionHandler(err -> {
          if (exceptionHandler != null) {
            exceptionHandler.handle(err);
          }
        });
        stream.handler(this);
      } else {
        stream.handler(null);
        stream.endHandler(null);
        stream.exceptionHandler(null);
      }
    }
    return this;
  }

  private void handleEvent(JsonEvent event) {
    if (demand != Long.MAX_VALUE) {
      demand--;
    }
    Handler<JsonEvent> handler = this.eventHandler;
    if (handler != null) {
      handler.handle(event);
    }
  }

  private void handleToken(JsonToken token) {
    try {
      switch (token) {
        case START_OBJECT: {
          BufferingHandler handler = objectHandler;
          if (handler != null) {
            tokenHandler = handler;
            handler.handle(token);
          } else {
            handleEvent(new JsonEventImpl(JsonEventType.START_OBJECT, currentField, null));
          }
          break;
        }
        case START_ARRAY: {
          BufferingHandler handler = arrayHandler;
          if (handler != null) {
            tokenHandler = handler;
            handler.handle(token);
          } else {
            handleEvent(new JsonEventImpl(JsonEventType.START_ARRAY, currentField, null));
          }
          break;
        }
        case FIELD_NAME: {
          currentField = parser.getCurrentName();
          break;
        }
        case VALUE_STRING: {
          String f = currentField;
          currentField = null;
          handleEvent(new JsonEventImpl(JsonEventType.VALUE, f, parser.getText()));
          break;
        }
        case VALUE_TRUE: {
          handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, Boolean.TRUE));
          break;
        }
        case VALUE_FALSE: {
          handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, Boolean.FALSE));
          break;
        }
        case VALUE_NULL: {
          handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, null));
          break;
        }
        case VALUE_NUMBER_INT: {
          handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, parser.getLongValue()));
          break;
        }
        case VALUE_NUMBER_FLOAT: {
          handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, parser.getDoubleValue()));
          break;
        }
        case END_OBJECT: {
          handleEvent(new JsonEventImpl(JsonEventType.END_OBJECT, null, null));
          break;
        }
        case END_ARRAY: {
          handleEvent(new JsonEventImpl(JsonEventType.END_ARRAY, null, null));
          break;
        }
        default:
          throw new UnsupportedOperationException("Token " + token + " not implemented");
      }
    } catch (IOException e) {
      throw new DecodeException(e.getMessage());
    }
  }

  @Override
  public void handle(Buffer event) {
    byte[] bytes = event.getBytes();
    try {
      parser.feedInput(bytes, 0, bytes.length);
    } catch (IOException e) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
        return;
      } else {
        throw new DecodeException(e.getMessage(), e);
      }
    }
    checkPending();
  }

  @Override
  public void end() {
    if (ended) {
      throw new IllegalStateException("Parsing already done");
    }
    ended = true;
    parser.endOfInput();
    checkPending();
  }

  private void checkPending() {
    try {
      while (true) {
        if (currentToken == null) {
          JsonToken next = parser.nextToken();
          if (next != null && next != JsonToken.NOT_AVAILABLE) {
            currentToken = next;
          }
        }
        if (currentToken == null) {
          if (ended) {
            if (endHandler != null) {
              endHandler.handle(null);
            }
            return;
          }
          break;
        } else {
          if (demand > 0L) {
            JsonToken token = currentToken;
            currentToken = null;
            tokenHandler.handle(token);
          } else {
            break;
          }
        }
      }
      if (demand == 0L) {
        if (stream != null) {
          stream.pause();
        }
      } else {
        if (stream != null) {
          stream.resume();
        }
      }
    } catch (IOException e) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
      } else {
        throw new DecodeException(e.getMessage());
      }
    } catch (Exception e) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(e);
      } else {
        throw e;
      }
    }
  }

  @Override
  public JsonParser objectEventMode() {
    if (objectHandler != null) {
      objectHandler = null;
      tokenHandler = this::handleToken;
    }
    return this;
  }

  @Override
  public JsonParser objectValueMode() {
    if (objectHandler == null) {
      BufferingHandler handler = new BufferingHandler();
      handler.handler = buffer -> {
        handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, new JsonObject(handler.convert(Map.class))));
      };
      objectHandler = handler;
    }
    return this;
  }

  @Override
  public JsonParser arrayEventMode() {
    if (arrayHandler != null) {
      arrayHandler = null;
      tokenHandler = this::handleToken;
    }
    return this;
  }

  @Override
  public JsonParser arrayValueMode() {
    if (arrayHandler == null) {
      BufferingHandler handler = new BufferingHandler();
      handler.handler = buffer -> {
        handleEvent(new JsonEventImpl(JsonEventType.VALUE, currentField, new JsonArray(handler.convert(List.class))));
      };
      arrayHandler = handler;
    }
    return this;
  }

  /**
   * A parser implementation that feeds from a list of tokens instead of bytes.
   */
  private static class TokenParser extends ParserBase {

    private ArrayDeque<Object> tokens = new ArrayDeque<>();
    private String text;

    private TokenParser(IOContext ctxt, int features) {
      super(ctxt, features);
    }

    @Override
    public JsonToken nextToken() throws IOException {
      if (tokens.isEmpty()) {
        return null;
      }
      text = null;
      _numTypesValid = NR_UNKNOWN;
      _numberLong = 0L;
      _numberDouble = 0L;
      _currToken = (JsonToken) tokens.removeFirst();
      if (_currToken == JsonToken.FIELD_NAME) {
        String field = (String) tokens.removeFirst();
        _parsingContext.setCurrentName(field);
        text = field;
      } else if (_currToken == JsonToken.VALUE_NUMBER_INT) {
        Long v = (Long) tokens.removeFirst();
        _numTypesValid = NR_LONG;
        _numberLong = v;
      } else if (_currToken == JsonToken.VALUE_NUMBER_FLOAT) {
        Double v = (Double) tokens.removeFirst();
        _numTypesValid = NR_DOUBLE;
        _numberDouble = v;
      } else if (_currToken == JsonToken.VALUE_STRING) {
        text = (String) tokens.removeFirst();
      }
      return _currToken;
    }

    @Override
    public String getText() {
      return text;
    }

    @Override
    public char[] getTextCharacters() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getTextLength() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getTextOffset() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ObjectCodec getCodec() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setCodec(ObjectCodec c) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void _closeInput() {
    }
  }

  private class BufferingHandler implements Handler<JsonToken> {

    Handler<Void> handler;
    int depth;
    TokenParser buffer;

    @Override
    public void handle(JsonToken event) {
      try {
        switch (event) {
          case START_OBJECT:
          case START_ARRAY:
            if (depth++ == 0) {
              JsonFactory factory = new JsonFactory();
              buffer = new TokenParser(new IOContext(factory._getBufferRecycler(), this, true), com.fasterxml.jackson.core.JsonParser.Feature.collectDefaults());
            }
            buffer.tokens.add(event);
            break;
          case FIELD_NAME:
            buffer.tokens.add(event);
            buffer.tokens.add(parser.currentName());
            break;
          case VALUE_NUMBER_INT:
            buffer.tokens.add(event);
            buffer.tokens.add(parser.getLongValue());
            break;
          case VALUE_NUMBER_FLOAT:
            buffer.tokens.add(event);
            buffer.tokens.add(parser.getDoubleValue());
            break;
          case VALUE_STRING:
            buffer.tokens.add(event);
            buffer.tokens.add(parser.getText());
            break;
          case VALUE_FALSE:
          case VALUE_TRUE:
          case VALUE_NULL:
            buffer.tokens.add(event);
            break;
          case END_OBJECT:
          case END_ARRAY:
            buffer.tokens.add(event);
            if (--depth == 0) {
              tokenHandler = JsonParserImpl.this::handleToken;
              handler.handle(null);
              buffer.close();
              buffer = null;
            }
            break;
          default:
            throw new UnsupportedOperationException("Not implemented " + event);
        }
      } catch (IOException e) {
        // Should not happen as we are buffering
        throw new VertxException(e);
      }
    }

    <T> T convert(Class<T> type) {
      return JacksonCodec.fromParser(buffer, type);
    }
  }

  @Override
  public JsonParser write(Buffer buffer) {
    handle(buffer);
    return this;
  }

  @Override
  public JsonParser exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }
}
