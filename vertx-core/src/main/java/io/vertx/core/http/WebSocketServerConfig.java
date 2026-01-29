/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.vertx.core.http.HttpServerOptions.*;

/**
 * WebSocket server configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class WebSocketServerConfig {

  private List<String> subProtocols;
  private Duration closingTimeout;
  private int maxFrameSize;
  private int maxMessageSize;
  private boolean usePerFrameCompression;
  private boolean usePerMessageCompression;
  private int compressionLevel;
  private boolean useUnmaskedFrames;
  private boolean useServerNoContext;
  private boolean useClientNoContext;

  public WebSocketServerConfig() {
    this.subProtocols = null;
    this.closingTimeout = Duration.ofSeconds(DEFAULT_WEBSOCKET_CLOSING_TIMEOUT);
    this.maxFrameSize = DEFAULT_MAX_WEBSOCKET_FRAME_SIZE;
    this.maxMessageSize = DEFAULT_MAX_WEBSOCKET_MESSAGE_SIZE;
    this.usePerFrameCompression = DEFAULT_PER_FRAME_WEBSOCKET_COMPRESSION_SUPPORTED;
    this.usePerMessageCompression = DEFAULT_PER_MESSAGE_WEBSOCKET_COMPRESSION_SUPPORTED;
    this.compressionLevel = DEFAULT_WEBSOCKET_COMPRESSION_LEVEL;
    this.useUnmaskedFrames = DEFAULT_ACCEPT_UNMASKED_FRAMES;
    this.useClientNoContext = DEFAULT_WEBSOCKET_PREFERRED_CLIENT_NO_CONTEXT;
    this.useServerNoContext = DEFAULT_WEBSOCKET_ALLOW_SERVER_NO_CONTEXT;
  }

  public WebSocketServerConfig(WebSocketServerConfig other) {
    this.subProtocols = other.subProtocols != null ? new ArrayList<>(other.subProtocols) : null;
    this.closingTimeout = other.closingTimeout;
    this.maxFrameSize = other.maxFrameSize;
    this.maxMessageSize = other.maxMessageSize;
    this.usePerFrameCompression = other.usePerFrameCompression;
    this.usePerMessageCompression = other.usePerMessageCompression;
    this.compressionLevel = other.compressionLevel;
    this.useUnmaskedFrames = other.useUnmaskedFrames;
    this.useClientNoContext = other.useClientNoContext;
    this.useServerNoContext = other.useServerNoContext;
  }

  /**
   * Add a WebSocket sub-protocol to the list supported by the server.
   *
   * @param subProtocol the sub-protocol to add
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig addWebSocketSubProtocol(String subProtocol) {
    Objects.requireNonNull(subProtocol, "Cannot add a null WebSocket sub-protocol");
    if (subProtocols == null) {
      subProtocols = new ArrayList<>();
    }
    subProtocols.add(subProtocol);
    return this;
  }
  /**
   * Set the WebSocket list of sub-protocol supported by the server.
   *
   * @param subProtocols  comma separated list of sub-protocols
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setSubProtocols(List<String> subProtocols) {
    this.subProtocols = subProtocols;
    return this;
  }

  /**
   * @return Get the WebSocket list of sub-protocol
   */
  public List<String> getSubProtocols() {
    return subProtocols;
  }

  /**
   * @return the amount of time (in seconds) a client WebSocket will wait until it closes TCP connection after receiving a close frame
   */
  public Duration getClosingTimeout() {
    return closingTimeout;
  }

  /**
   * Set the amount of time a server WebSocket will wait until it closes the TCP connection
   * after sending a close frame.
   *
   * <p> When a server closes a WebSocket, it should wait the client close frame to close the TCP connection.
   * This timeout will close the TCP connection on the server when it expires. When the TCP
   * connection is closed receiving the close frame, the {@link WebSocket#exceptionHandler} instead
   * of the {@link WebSocket#endHandler} will be called.
   *
   * <p> Set to {@code 0L} closes the TCP connection immediately after sending the close frame.
   *
   * <p> Set to a negative value to disable it.
   *
   * @param closingTimeout the duration of the timeout
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setClosingTimeout(Duration closingTimeout) {
    this.closingTimeout = closingTimeout;
    return this;
  }

  /**
   * @return the maximum WebSocket frame size
   */
  public int getMaxFrameSize() {
    return maxFrameSize;
  }

  /**
   * Set the maximum WebSocket frames size
   *
   * @param maxFrameSize  the maximum frame size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setMaxFrameSize(int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
    return this;
  }

  /**
   * @return  the maximum WebSocket message size
   */
  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  /**
   * Set the maximum WebSocket message size
   *
   * @param maxMessageSize  the maximum message size in bytes.
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
    return this;
  }

  /**
   * @return whether the endpoint will send or accept the per-frame deflate compression extension
   */
  public boolean getUsePerFrameCompression() {
    return this.usePerFrameCompression;
  }

  /**
   * Set whether the WebSocket per-frame deflate compression extension is used or supported.
   *
   * @param use whether the per-frame deflate compression extension will be sent or accepted
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setUsePerFrameCompression(boolean use) {
    this.usePerFrameCompression = use;
    return this;
  }

  /**
   * @return whether the endpoint will send or accept the per-frame deflate compression extension
   */
  public boolean getUsePerMessageCompression() {
    return this.usePerMessageCompression;
  }

  /**
   * Set whether the WebSocket per-message deflate compression extension is used or supported.
   *
   * @param use whether the per-frame deflate compression extension will be sent or accepted
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setUsePerMessageCompression(boolean use) {
    this.usePerMessageCompression = use;
    return this;
  }

  /**
   * @return the current WebSocket deflate compression level
   */
  public int getCompressionLevel() {
    return this.compressionLevel;
  }

  /**
   * Set the WebSocket compression level.
   *
   * @param compressionLevel the compression level
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setCompressionLevel(int compressionLevel) {
    this.compressionLevel = compressionLevel;
    return this;
  }

  /**
   * @return whether WebSocket unmasked frames are used or supported
   */
  public boolean isUseUnmaskedFrames() {
    return useUnmaskedFrames;
  }

  /**
   * Set whether WebSocket unmasked frames are used or supported
   *
   * @param use whether the masked frames are used or supported
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setUseUnmaskedFrames(boolean use) {
    this.useUnmaskedFrames = use;
    return this;
  }

  /**
   * @return whether the WebSocket {@code server_no_context_takeover} parameter of the per-message deflate compression
   * is used or supported.
   */
  public boolean getUseServerNoContext() {
    return this.useServerNoContext;
  }

  /**
   * Set whether the WebSocket {@code server_no_context_takeover} parameter of the per-message deflate compression
   * is used or supported.
   *
   * @param use whether the WebSocket {@code server_no_context_takeover} parameter of the per-message deflate compression
   *            is used or supported.
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setUseServerNoContext(boolean use) {
    this.useServerNoContext = use;
    return this;
  }

  /**
   * @return use whether the WebSocket {@code client_no_context_takeover} parameter of the per-message deflate compression
   * is used or supported.
   */
  public boolean getUseClientNoContext() {
    return this.useClientNoContext;
  }

  /**
   * Set whether the WebSocket {@code client_no_context_takeover} parameter of the per-message deflate compression
   * is used or supported.
   *
   * @param use whether the WebSocket {@code client_no_context_takeover} parameter of the per-message deflate compression
   *            is used or supported.
   * @return a reference to this, so the API can be used fluently
   */
  public WebSocketServerConfig setUseClientNoContext(boolean use) {
    this.useClientNoContext = use;
    return this;
  }
}
