/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.vertx.java.core.streams;

import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import org.vertx.java.core.streams.impl.EmbeddedChannelReadStream;
import org.vertx.java.core.streams.impl.EmbeddedChannelWriteStream;


/**
 * Utility class which allows to create wrappers around {@link ReadStream} and {@link WriteStream} implementations
 * which enabled compression.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class Compression {

  /**
   * Wrap the given {@link ReadStream} and decompress its content with ZLIB.
   */
  public static ReadStream<?> zlib(ReadStream<?> readStream) {
    return EmbeddedChannelReadStream.create(readStream, new JdkZlibDecoder(ZlibWrapper.ZLIB));
  }

  /**
   * Wrap the given {@link ReadStream} and decompress its content with ZLIB using the given dictionary.
   */
  public static ReadStream<?> zlib(ReadStream<?> readStream, byte[] dictionary) {
    return EmbeddedChannelReadStream.create(readStream, new JdkZlibDecoder(dictionary.clone()));
  }

  /**
   * Wrap the given {@link WriteStream} and compress the written buffers via ZLIB.
   */
  public static WriteStream<?> zlib(WriteStream<?> writeStream) {
    return EmbeddedChannelWriteStream.create(writeStream, new JdkZlibEncoder(ZlibWrapper.ZLIB));
  }

  /**
   * Wrap the given {@link WriteStream} and compress the written buffers via ZLIB using the given compressionLevel.
   */
  public static WriteStream<?> zlib(WriteStream<?> writeStream, int compressionLevel) {
    return EmbeddedChannelWriteStream.create(writeStream, new JdkZlibEncoder(ZlibWrapper.ZLIB, compressionLevel));
  }

  /**
   * Wrap the given {@link WriteStream} and compress the written buffers via ZLIB using the
   * the given dictionary.
   */
  public static WriteStream<?> zlib(WriteStream<?> writeStream, byte[] dictionary) {
    return EmbeddedChannelWriteStream.create(writeStream, new JdkZlibEncoder(dictionary.clone()));
  }

  /**
   * Wrap the given {@link WriteStream} and compress the written buffers via ZLIB using the given compressionLevel using
   * the given dictionary.
   */
  public static WriteStream<?> zlib(WriteStream<?> writeStream, int compressionLevel, byte[] dictionary) {
    return EmbeddedChannelWriteStream.create(writeStream, new JdkZlibEncoder(compressionLevel, dictionary.clone()));
  }

  /**
   * Wrap the given {@link ReadStream} and decompress its content with GZIP.
   */
  public static ReadStream<?> gzip(ReadStream<?> readStream) {
    return EmbeddedChannelReadStream.create(readStream, new JdkZlibDecoder(ZlibWrapper.GZIP));
  }

  /**
   * Wrap the given {@link WriteStream} and compress the written buffers via GZIP.
   */
  public static WriteStream<?> gzip(WriteStream<?> writeStream) {
    return EmbeddedChannelWriteStream.create(writeStream, new JdkZlibEncoder(ZlibWrapper.GZIP));
  }

  /**
   * Wrap the given {@link WriteStream} and compress the written buffers via GZIP using the given compressionLevel.
   */
  public static WriteStream<?> gzip(WriteStream<?> writeStream, int compressionLevel) {
    return EmbeddedChannelWriteStream.create(writeStream, new JdkZlibEncoder(ZlibWrapper.GZIP, compressionLevel));
  }

  private Compression() {
    // only contains static methods
  }
}
