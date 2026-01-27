package io.vertx.core.http;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.Handler;
import io.vertx.core.impl.Arguments;

import static io.vertx.core.http.HttpServerOptions.*;

/**
 * HTTP/1.x server configuration.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class Http1ServerConfig {

  private int maxChunkSize;
  private int maxInitialLineLength;
  private int maxHeaderSize;
  private int maxFormAttributeSize;
  private int maxFormFields;
  private int maxFormBufferedBytes;
  private int decoderInitialBufferSize;

  public Http1ServerConfig() {
    maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;
    maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;
    maxHeaderSize = DEFAULT_MAX_HEADER_SIZE;
    maxFormAttributeSize = DEFAULT_MAX_FORM_ATTRIBUTE_SIZE;
    maxFormFields = DEFAULT_MAX_FORM_FIELDS;
    maxFormBufferedBytes = DEFAULT_MAX_FORM_BUFFERED_SIZE;
    decoderInitialBufferSize = DEFAULT_DECODER_INITIAL_BUFFER_SIZE;
  }

  public Http1ServerConfig(Http1ServerConfig other) {
    this.maxChunkSize = other.getMaxChunkSize();
    this.maxInitialLineLength = other.getMaxInitialLineLength();
    this.maxHeaderSize = other.getMaxHeaderSize();
    this.maxFormAttributeSize = other.getMaxFormAttributeSize();
    this.maxFormFields = other.getMaxFormFields();
    this.maxFormBufferedBytes = other.getMaxFormBufferedBytes();
    this.decoderInitialBufferSize = other.getDecoderInitialBufferSize();
  }

  /**
   * Set the maximum HTTP chunk size that {@link HttpServerRequest#handler(Handler)} will receive
   *
   * @param maxChunkSize the maximum chunk size
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setMaxChunkSize(int maxChunkSize) {
    this.maxChunkSize = maxChunkSize;
    return this;
  }

  /**
   * @return the maximum HTTP chunk size that {@link HttpServerRequest#handler(Handler)} will receive
   */
  public int getMaxChunkSize() {
    return maxChunkSize;
  }


  /**
   * @return the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   */
  public int getMaxInitialLineLength() {
    return maxInitialLineLength;
  }

  /**
   * Set the maximum length of the initial line for HTTP/1.x (e.g. {@code "GET / HTTP/1.0"})
   *
   * @param maxInitialLineLength the new maximum initial length
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setMaxInitialLineLength(int maxInitialLineLength) {
    this.maxInitialLineLength = maxInitialLineLength;
    return this;
  }

  /**
   * @return Returns the maximum length of all headers for HTTP/1.x
   */
  public int getMaxHeaderSize() {
    return maxHeaderSize;
  }

  /**
   * Set the maximum length of all headers for HTTP/1.x .
   *
   * @param maxHeaderSize the new maximum length
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setMaxHeaderSize(int maxHeaderSize) {
    this.maxHeaderSize = maxHeaderSize;
    return this;
  }

  /**
   * @return Returns the maximum size of a form attribute
   */
  public int getMaxFormAttributeSize() {
    return maxFormAttributeSize;
  }

  /**
   * Set the maximum size of a form attribute. Set to {@code -1} to allow unlimited length
   *
   * @param maxSize the new maximum size
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setMaxFormAttributeSize(int maxSize) {
    this.maxFormAttributeSize = maxSize;
    return this;
  }

  /**
   * @return Returns the maximum number of form fields
   */
  public int getMaxFormFields() {
    return maxFormFields;
  }

  /**
   * Set the maximum number of fields of a form. Set to {@code -1} to allow unlimited number of attributes
   *
   * @param maxFormFields the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setMaxFormFields(int maxFormFields) {
    this.maxFormFields = maxFormFields;
    return this;
  }

  /**
   * @return Returns the maximum number of bytes a server can buffer when decoding a form
   */
  public int getMaxFormBufferedBytes() {
    return maxFormBufferedBytes;
  }

  /**
   * Set the maximum number of bytes a server can buffer when decoding a form. Set to {@code -1} to allow unlimited length
   *
   * @param maxFormBufferedBytes the new maximum
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setMaxFormBufferedBytes(int maxFormBufferedBytes) {
    this.maxFormBufferedBytes = maxFormBufferedBytes;
    return this;
  }

  /**
   * @return the initial buffer size for the HTTP decoder
   */
  public int getDecoderInitialBufferSize() { return decoderInitialBufferSize; }

  /**
   * Set the initial buffer size for the HTTP decoder
   * @param decoderInitialBufferSize the initial size
   * @return a reference to this, so the API can be used fluently
   */
  public Http1ServerConfig setDecoderInitialBufferSize(int decoderInitialBufferSize) {
    Arguments.require(decoderInitialBufferSize > 0, "initialBufferSizeHttpDecoder must be > 0");
    this.decoderInitialBufferSize = decoderInitialBufferSize;
    return this;
  }
}
