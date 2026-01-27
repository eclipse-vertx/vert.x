package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;

import java.time.Duration;

import static io.vertx.core.http.HttpServerOptions.*;

/**
 * HTTP/2 server configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http2ServerConfig {

  private Http2Settings initialSettings;
  private boolean clearTextEnabled;
  private int connectionWindowSize;
  private boolean multiplexImplementation;
  private int rstFloodMaxRstFramePerWindow;
  private Duration rstFloodWindowDuration;

  public Http2ServerConfig() {
    initialSettings = new Http2Settings().setMaxConcurrentStreams(DEFAULT_INITIAL_SETTINGS_MAX_CONCURRENT_STREAMS);
    clearTextEnabled = DEFAULT_HTTP2_CLEAR_TEXT_ENABLED;
    connectionWindowSize = DEFAULT_HTTP2_CONNECTION_WINDOW_SIZE;
    rstFloodMaxRstFramePerWindow = DEFAULT_HTTP2_RST_FLOOD_MAX_RST_FRAME_PER_WINDOW;
    rstFloodWindowDuration = Duration.of(DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION, DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION_TIME_UNIT.toChronoUnit());
    multiplexImplementation = DEFAULT_HTTP_2_MULTIPLEX_IMPLEMENTATION;
  }

  public Http2ServerConfig(Http2ServerConfig other) {
    this.initialSettings = other.initialSettings != null ? new Http2Settings(other.initialSettings) : null;
    this.clearTextEnabled = other.clearTextEnabled;
    this.connectionWindowSize = other.connectionWindowSize;
    this.rstFloodMaxRstFramePerWindow = other.rstFloodMaxRstFramePerWindow;
    this.rstFloodWindowDuration = other.rstFloodWindowDuration;
    this.multiplexImplementation = other.multiplexImplementation;
  }

  /**
   * @return the max number of RST frame allowed per time window
   */
  public int getRstFloodMaxRstFramePerWindow() {
    return rstFloodMaxRstFramePerWindow;
  }

  /**
   * Set the max number of RST frame allowed per time window, this is used to prevent HTTP/2 RST frame flood DDOS
   * attacks. The default value is {@link HttpServerOptions#DEFAULT_HTTP2_RST_FLOOD_MAX_RST_FRAME_PER_WINDOW}, setting zero or a negative value, disables flood protection.
   *
   * @param rstFloodMaxRstFramePerWindow the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ServerConfig setRstFloodMaxRstFramePerWindow(int rstFloodMaxRstFramePerWindow) {
    this.rstFloodMaxRstFramePerWindow = rstFloodMaxRstFramePerWindow;
    return this;
  }

  /**
   * @return the duration of the time window when checking the max number of RST frames.
   */
  public Duration getRstFloodWindowDuration() {
    return rstFloodWindowDuration;
  }

  /**
   * Set the duration of the time window when checking the max number of RST frames, this is used to prevent HTTP/2 RST frame flood DDOS
   * attacks. The default value is {@link HttpServerOptions#DEFAULT_HTTP2_RST_FLOOD_WINDOW_DURATION}, setting zero or a negative value, disables flood protection.
   *
   * @param rstFloodWindowDuration the new duration
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ServerConfig setRstFloodWindowDuration(Duration rstFloodWindowDuration) {
    this.rstFloodWindowDuration = rstFloodWindowDuration;
    return this;
  }

  /**
   * @return whether the server accepts HTTP/2 over clear text connections
   */
  public boolean isClearTextEnabled() {
    return clearTextEnabled;
  }

  /**
   * Set whether HTTP/2 over clear text is enabled or disabled, default is enabled.
   *
   * @param clearTextEnabled whether to accept HTTP/2 over clear text
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ServerConfig setClearTextEnabled(boolean clearTextEnabled) {
    this.clearTextEnabled = clearTextEnabled;
    return this;
  }

  /**
   * @return the default HTTP/2 connection window size
   */
  public int getConnectionWindowSize() {
    return connectionWindowSize;
  }

  /**
   * Set the default HTTP/2 connection window size. It overrides the initial window
   * size set by {@link Http2Settings#getInitialWindowSize}, so the connection window size
   * is greater than for its streams, in order the data throughput.
   * <p/>
   * A value of {@code -1} reuses the initial window size setting.
   *
   * @param connectionWindowSize the window size applied to the connection
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ServerConfig setConnectionWindowSize(int connectionWindowSize) {
    this.connectionWindowSize = connectionWindowSize;
    return this;
  }

  /**
   * @return whether to use the HTTP/2 implementation based on multiplexed channel
   */
  public boolean getMultiplexImplementation() {
    return multiplexImplementation;
  }

  /**
   * Set which HTTP/2 implementation to use
   *
   * @param multiplexImplementation whether to use the HTTP/2 multiplex implementation
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ServerConfig setMultiplexImplementation(boolean multiplexImplementation) {
    this.multiplexImplementation = multiplexImplementation;
    return this;
  }

  /**
   * @return the initial HTTP/2 connection settings
   */
  public Http2Settings getInitialSettings() {
    return initialSettings;
  }

  /**
   * Set the HTTP/2 connection settings immediatly sent by the server when a client connects.
   *
   * @param settings the settings value
   * @return a reference to this, so the API can be used fluently
   */
  public Http2ServerConfig setInitialSettings(Http2Settings settings) {
    this.initialSettings = settings;
    return this;
  }
}
