/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import com.fasterxml.jackson.core.JsonLocation;
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
import java.util.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class JsonParserImpl implements JsonParser {

  private final NonBlockingJsonParser parser;
  private Handler<JsonEventImpl> tokenHandler = this::handleEvent;
  private Handler<JsonEvent> eventHandler;
  private boolean objectValueMode;
  private boolean arrayValueMode;
  private Handler<Throwable> exceptionHandler;
  private String currentField;
  private Handler<Void> endHandler;
  private long demand = Long.MAX_VALUE;
  private boolean ended;
  private final ReadStream<Buffer> stream;
  private boolean emitting;
  private final Deque<JsonEventImpl> pending = new ArrayDeque<>();
  private List<IOException> collectedExceptions;

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
    if (pending.size() > 0 || !ended) {
      endHandler = handler;
    }
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

  private void handleEvent(JsonEventImpl event) {
    if (event.type() == JsonEventType.START_OBJECT && objectValueMode) {
      BufferingHandler handler = new BufferingHandler();
      handler.handler = buffer -> {
        tokenHandler = this::handleEvent;
        handleEvent(new JsonEventImpl(null, JsonEventType.VALUE, event.fieldName(), new JsonObject(handler.convert(Map.class))));
      };
      tokenHandler = handler;
      handler.handle(new JsonEventImpl(JsonToken.START_OBJECT, JsonEventType.START_OBJECT, null, null));
    } else if (event.type() == JsonEventType.START_ARRAY && arrayValueMode) {
      BufferingHandler handler = new BufferingHandler();
      handler.handler = buffer -> {
        tokenHandler = this::handleEvent;
        handleEvent(new JsonEventImpl(null, JsonEventType.VALUE, event.fieldName(), new JsonArray(handler.convert(List.class))));
      };
      tokenHandler = handler;
      handler.handle(new JsonEventImpl(JsonToken.START_ARRAY, JsonEventType.START_ARRAY, null, null));
    } else {
      if (demand != Long.MAX_VALUE) {
        demand--;
      }
      if (eventHandler != null) {
        eventHandler.handle(event);
      }
    }
  }

  private void handle(IOException ioe) {
    if (collectedExceptions == null) {
      collectedExceptions = new ArrayList<>();
    }
    collectedExceptions.add(ioe);
  }

  @Override
  public void handle(Buffer data) {
    byte[] bytes = data.getBytes();
    try {
      parser.feedInput(bytes, 0, bytes.length);
    } catch (IOException e) {
      handle(e);
    }
    checkTokens();
    checkPending();
    checkExceptions();
  }

  @Override
  public void end() {
    if (ended) {
      throw new IllegalStateException("Parsing already done");
    }
    ended = true;
    parser.endOfInput();
    checkTokens();
    checkPending();
    checkExceptions();
  }

  private void checkTokens() {
    JsonLocation prevLocation = null;
    while (true) {
      JsonToken token;
      try {
        token = parser.nextToken();
      } catch (IOException e) {
        JsonLocation location = parser.currentLocation();
        if (prevLocation != null) {
          if (location.equals(prevLocation)) {
            // If we haven't done any progress, give up
            return;
          }
        }
        prevLocation = location;
        handle(e);
        continue;
      }
      if (token == null || token == JsonToken.NOT_AVAILABLE) {
        break;
      }
      prevLocation = null;
      String field = currentField;
      currentField = null;
      JsonEventImpl event;
      switch (token) {
        case START_OBJECT: {
          event = new JsonEventImpl(token, JsonEventType.START_OBJECT, field, null);
          break;
        }
        case START_ARRAY: {
          event = new JsonEventImpl(token, JsonEventType.START_ARRAY, field, null);
          break;
        }
        case FIELD_NAME: {
          try {
            currentField = parser.getCurrentName();
          } catch (IOException e) {
            handle(e);
          }
          continue;
        }
        case VALUE_STRING: {
          try {
            event = new JsonEventImpl(token, JsonEventType.VALUE, field, parser.getText());
          } catch (IOException e) {
            handle(e);
            continue;
          }
          break;
        }
        case VALUE_TRUE: {
          event = new JsonEventImpl(token, JsonEventType.VALUE, field, Boolean.TRUE);
          break;
        }
        case VALUE_FALSE: {
          event = new JsonEventImpl(token, JsonEventType.VALUE, field, Boolean.FALSE);
          break;
        }
        case VALUE_NULL: {
          event = new JsonEventImpl(token, JsonEventType.VALUE, field, null);
          break;
        }
        case VALUE_NUMBER_INT: {
          try {
            Number number = parser.getNumberValue();
            if (number instanceof Integer) {
              // Backward compat
              number = (long) (int) number;
            }
            event = new JsonEventImpl(token, JsonEventType.VALUE, field, number);
          } catch (IOException e) {
            handle(e);
            continue;
          }
          break;
        }
        case VALUE_NUMBER_FLOAT: {
          try {
            event = new JsonEventImpl(token, JsonEventType.VALUE, field, parser.getDoubleValue());
          } catch (IOException e) {
            handle(e);
            continue;
          }
          break;
        }
        case END_OBJECT: {
          event = new JsonEventImpl(token, JsonEventType.END_OBJECT, null, null);
          break;
        }
        case END_ARRAY: {
          event = new JsonEventImpl(token, JsonEventType.END_ARRAY, null, null);
          break;
        }
        default:
          throw new UnsupportedOperationException("Token " + token + " not implemented");
      }
      pending.add(event);
    }
  }

  private void checkPending() {
    if (!emitting) {
      emitting = true;
      try {
        while (true) {
          if (demand > 0L) {
            JsonEventImpl currentToken = pending.poll();
            if (currentToken == null) {
              break;
            } else {
              tokenHandler.handle(currentToken);
            }
          } else {
            break;
          }
        }
        if (ended) {
          if (pending.isEmpty()) {
            checkExceptions();
            Handler<Void> handler = endHandler;
            endHandler = null;
            if (handler != null) {
              handler.handle(null);
            }
          }
        } else {
          if (demand == 0L) {
            if (stream != null) {
              stream.pause();
            }
          } else {
            if (stream != null) {
              stream.resume();
            }
          }
        }
      } catch (Exception e) {
        if (exceptionHandler != null) {
          exceptionHandler.handle(e);
        } else {
          throw e;
        }
      } finally {
        emitting = false;
      }
    }
  }

  private void checkExceptions() {
    List<IOException> exceptions = collectedExceptions;
    collectedExceptions = null;
    if (exceptions != null && exceptions.size() > 0) {
      if (exceptionHandler != null) {
        for (IOException ioe : exceptions) {
          exceptionHandler.handle(ioe);
        }
      } else {
        IOException ioe = exceptions.get(0);
        throw new DecodeException(ioe.getMessage(), ioe);
      }
    }
  }

  @Override
  public JsonParser objectEventMode() {
    objectValueMode = false;
    return this;
  }

  @Override
  public JsonParser objectValueMode() {
    objectValueMode = true;
    return this;
  }

  @Override
  public JsonParser arrayEventMode() {
    arrayValueMode = false;
    return this;
  }

  @Override
  public JsonParser arrayValueMode() {
    arrayValueMode = true;
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

  private class BufferingHandler implements Handler<JsonEventImpl> {

    Handler<Void> handler;
    int depth;
    TokenParser buffer;

    @Override
    public void handle(JsonEventImpl event) {
      String fieldName = event.fieldName();
      if (fieldName != null) {
        buffer.tokens.add(JsonToken.FIELD_NAME);
        buffer.tokens.add(fieldName);
      }
      try {
        switch (event.type()) {
          case START_OBJECT:
          case START_ARRAY:
            if (depth++ == 0) {
              JsonFactory factory = new JsonFactory();
              buffer = new TokenParser(new IOContext(factory._getBufferRecycler(), this, true), com.fasterxml.jackson.core.JsonParser.Feature.collectDefaults());
            }
            buffer.tokens.add(event.token());
            break;
          case VALUE:
            JsonToken token = event.token();
            buffer.tokens.add(token);
            if (token != JsonToken.VALUE_FALSE && token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_NULL) {
              buffer.tokens.add(event.value());
            }
            break;
          case END_OBJECT:
          case END_ARRAY:
            buffer.tokens.add(event.token());
            if (--depth == 0) {
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
